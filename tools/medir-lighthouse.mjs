#!/usr/bin/env node
// Mide Lighthouse sobre el build de producción servido, en móvil y con estrangulamiento.
//
// Existe porque la primera medición de la Fase 6 fue **inválida** y nadie se habría dado cuenta
// mirando las cifras: la ficha salió con SEO 61 y `noindex,nofollow`, que es el respaldo de su ruta
// cuando el producto no cargó. El motivo: en el navegador `baseUrl()` es relativa a propósito —en
// producción el balanceador enruta `/api` al backend en el mismo dominio— pero **el servidor SSR
// construido no hace ese proxy; solo lo hace `ng serve` con `proxy.conf.json`**. Servir el build a
// secas deja la consulta del producto muriendo tras hidratar.
//
// Este archivo levanta ese proxy y, antes de medir nada, **comprueba que la ficha cargó de verdad**.
// Un arnés que puede producir una medición inválida en silencio no sirve de arnés.
//
// Por la misma razón mide **tres veces cada pantalla y se queda con la mediana**, y dice al lado
// cuánto se separaron las muestras: una sola corrida del mismo build llegó a moverse 22 puntos de
// rendimiento, de modo que una cifra suelta puede inventar una regresión o tapar una real.
//
// Cada corrida puede guardarse con nombre, y dos corridas guardadas se comparan sin volver a
// medir — con la banda de sus propias muestras al lado, que es lo que distingue una mejora de la
// máquina teniendo un mal rato:
//   npm run lighthouse -- --etiqueta base
//   ...se hace el cambio...
//   npm run lighthouse -- --etiqueta fuentes
//   npm run lighthouse -- --comparar base fuentes     (no necesita API, ni build, ni Chrome)
//
// Requiere PostgreSQL y la API arriba:
//   docker compose up -d && (cd apps/api && gradlew.bat bootRun)
import { execSync } from "node:child_process";
import { spawn } from "node:child_process";
import { createServer, request as pedir } from "node:http";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const PUERTO_API = 8080;
const PUERTO_SSR = 4000;
const PUERTO_PROXY = 4300;
const BASE = `http://localhost:${PUERTO_PROXY}`;

const sinBuild = process.argv.includes("--sin-build");
const INFORMES = join(RAIZ, "apps/web/lighthouse");
const procesos = [];

/**
 * Dónde escribe esta corrida.
 *
 * <p>Sin etiqueta, donde siempre. Con `--etiqueta fuentes`, en `lighthouse/fuentes/`, y ahí está
 * el punto: el arnés escribía siempre `<pantalla>.json`, así que medir dos veces —que es lo único
 * que este arnés sirve para hacer— borraba el "antes" antes de que nadie lo leyera. Los puntajes
 * sobrevivían en `resumen.json`; el desglose del hilo principal, que es lo que explica un cambio,
 * no.
 */
function leerEtiqueta() {
  const posicion = process.argv.indexOf("--etiqueta");
  if (posicion === -1) return null;
  const nombre = process.argv[posicion + 1];
  if (!nombre || !/^[a-z0-9][a-z0-9-]*$/i.test(nombre)) {
    throw new Error(
      `--etiqueta pide un nombre simple (letras, digitos y guiones), no '${nombre}'.\n` +
        `Es un nombre de carpeta dentro de apps/web/lighthouse/.`,
    );
  }
  return nombre;
}

function carpetaDe(etiqueta) {
  return etiqueta ? join(INFORMES, etiqueta) : INFORMES;
}

function etiquetasGuardadas() {
  if (!existsSync(INFORMES)) return [];
  return readdirSync(INFORMES, { withFileTypes: true })
    .filter(
      (entrada) =>
        entrada.isDirectory() && existsSync(join(INFORMES, entrada.name, "resumen.json")),
    )
    .map((entrada) => entrada.name);
}

// Se resuelve dentro de `main`, no aquí: un throw en el nivel del módulo sale como volcado de
// pila, y el resto de los errores de este arnés salen como una frase que se entiende.
let MUESTRAS = 3;

/**
 * Tres muestras por pantalla, y la mediana.
 *
 * <p>Con una sola muestra el arnés puede inventar una regresión o taparla: entre dos corridas del
 * mismo build, cinco minutos aparte, el rendimiento de la portada se movió 22 puntos
 * (docs/09-plan-de-arranque.md). Un instrumento con esa dispersión no permite decir si un cambio
 * mejoró algo, que es para lo único que se mide.
 *
 * <p>Se deja bajar a 1 con `--muestras 1` para probar el arnés mismo —levanta proxy, SSR y Chrome
 * igual—, no para medir. Cualquier cifra de una sola muestra no significa nada.
 */
function leerMuestras() {
  const posicion = process.argv.indexOf("--muestras");
  if (posicion === -1) return 3;
  const pedido = process.argv[posicion + 1];
  const valor = Number(pedido);
  if (!Number.isInteger(valor) || valor < 1) {
    throw new Error(`--muestras pide un entero de 1 en adelante, no '${pedido}'.`);
  }
  return valor;
}

function log(mensaje) {
  console.log(mensaje);
}

async function responde(url) {
  try {
    const respuesta = await fetch(url);
    return respuesta.ok;
  } catch {
    return false;
  }
}

async function esperar(url, segundos, queEs) {
  for (let intento = 0; intento < segundos; intento++) {
    if (await responde(url)) return;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`${queEs} no respondió en ${segundos} s (${url})`);
}

/**
 * `/api` al backend y todo lo demás al servidor SSR: exactamente lo que hace el balanceador en
 * producción (docs/07-infra-gcp.md) y lo que `ng serve` hace en desarrollo. Sin esto la medición
 * mide otra aplicación.
 */
function levantarProxy() {
  const servidor = createServer((entrante, saliente) => {
    const alApi = entrante.url.startsWith("/api");
    const opciones = {
      hostname: "localhost",
      port: alApi ? PUERTO_API : PUERTO_SSR,
      path: entrante.url,
      method: entrante.method,
      headers: entrante.headers,
    };
    const peticion = pedir(opciones, (respuesta) => {
      saliente.writeHead(respuesta.statusCode, respuesta.headers);
      respuesta.pipe(saliente);
    });
    peticion.on("error", () => {
      saliente.writeHead(502);
      saliente.end("proxy: el destino no respondió");
    });
    entrante.pipe(peticion);
  });
  servidor.listen(PUERTO_PROXY);
  return servidor;
}

function levantarSsr() {
  const proceso = spawn(
    process.execPath,
    [join(RAIZ, "apps/web/dist/tecnosport-web/server/server.mjs")],
    {
      env: {
        ...process.env,
        PORT: String(PUERTO_SSR),
        // Apunta al proxy, no al backend: así el render del servidor pasa por el mismo camino que
        // el del navegador, que es lo que se quiere medir.
        API_URL_PUBLICA: BASE,
        NG_ALLOWED_HOSTS: "*",
      },
      stdio: "ignore",
    },
  );
  procesos.push(proceso);
  return proceso;
}

/**
 * Un slug real de la siembra: medir la ficha de un slug inventado mediría la pantalla de error.
 *
 * <p>Es además la primera comprobación de que `/api` **funciona a través del proxy**, que es la
 * condición de la que dependía la medición inválida de la Fase 6. Se comprueba el código antes de
 * leer el cuerpo: sin eso, un 502 del proxy moría con "Unexpected token p is not valid JSON", que
 * no le dice nada a quien lo lea.
 */
async function primerSlug() {
  const url = `${BASE}/api/v1/productos?tamano=1`;
  const respuesta = await fetch(url);
  if (!respuesta.ok) {
    throw new Error(
      `El catalogo no responde a traves del proxy (HTTP ${respuesta.status} en ${url}).
` +
        `Sin ese camino la medicion seria invalida: es exactamente la trampa de la Fase 6.`,
    );
  }
  const datos = await respuesta.json();
  const slug = datos.items?.[0]?.slug;
  if (!slug) throw new Error("La siembra no devolvió ningún producto: no hay ficha que medir.");
  return slug;
}

/**
 * La comprobación que convierte esto en un arnés y no en un script. Si la ficha sale `noindex` es
 * que el producto no cargó — el fallo exacto que invalidó la primera medición— y las cifras que
 * salgan después no significan nada.
 */
async function exigirQueLaFichaCargue(url) {
  const html = await (await fetch(url)).text();
  if (html.includes("noindex")) {
    throw new Error(
      `La ficha salió con noindex: el producto no cargó y la medición seria invalida.\n` +
        `Es el fallo documentado en docs/09-plan-de-arranque.md — revisa que la API responda a traves del proxy (${BASE}/api/v1/productos).`,
    );
  }
}

async function medirUnaVez(url, puertoChrome, lighthouse) {
  const resultado = await lighthouse(
    url,
    { port: puertoChrome, output: "json", logLevel: "error" },
    undefined,
  );
  const c = resultado.lhr.categories;
  return {
    puntajes: {
      rendimiento: Math.round(c.performance.score * 100),
      accesibilidad: Math.round(c.accessibility.score * 100),
      "buenas practicas": Math.round(c["best-practices"].score * 100),
      seo: Math.round(c.seo.score * 100),
    },
    metricas: metricasDe(resultado.lhr),
    informe: resultado.report,
  };
}

/**
 * Las seis cifras que explican un cambio, sacadas de cada muestra y no solo de la mediana.
 *
 * <p>Son las que hicieron falta para cerrar la deuda 19 y las que había que ir a rescatar a mano
 * del informe grande antes de que la corrida siguiente lo pisara. El peso y los bytes de
 * tipografía no dependen de la máquina; los cuatro tiempos sí, y por eso se guardan **por
 * muestra**: sin el rango, comparar dos medianas es volver a la cifra suelta que la deuda 17
 * quitó de en medio.
 *
 * <p>Cada una se lee con guardia: una auditoría que Lighthouse no produce —pasa, según la
 * pantalla y la versión— tiene que dejar un hueco, no tumbar la corrida ni escribir un cero que
 * después se lee como una mejora.
 */
function metricasDe(lhr) {
  const a = lhr.audits ?? {};
  const ms = (id) => (typeof a[id]?.numericValue === "number" ? Math.round(a[id].numericValue) : null);
  const grupo = (nombre) => {
    const fila = a["mainthread-work-breakdown"]?.details?.items?.find((i) => i.group === nombre);
    return fila ? Math.round(fila.duration) : null;
  };
  const tipografias = (a["network-requests"]?.details?.items ?? [])
    .filter((i) => i.resourceType === "Font")
    .reduce((suma, i) => suma + (i.transferSize ?? 0), 0);
  return {
    fcp: ms("first-contentful-paint"),
    lcp: ms("largest-contentful-paint"),
    tbt: ms("total-blocking-time"),
    "eval. scripts": grupo("scriptEvaluation"),
    "estilo y layout": grupo("styleLayout"),
    "peso kB": Math.round((a["total-byte-weight"]?.numericValue ?? 0) / 1024) || null,
    "fuentes kB": Math.round(tipografias / 1024) || null,
  };
}

/**
 * Mide una pantalla `MUESTRAS` veces y devuelve la mediana, con la dispersión al lado.
 *
 * <p>La mediana se elige **por rendimiento**, que es la única categoría que se mueve entre
 * corridas del mismo build: accesibilidad, buenas prácticas y SEO salen del DOM y no del reloj. Y
 * se guarda esa corrida **entera** en vez de un promedio por categoría, para que el informe del
 * disco corresponda a una medición que ocurrió de verdad: un promedio deja auditorías que no
 * cuadran con sus propios puntajes, y quien lo abra dentro de un mes no tiene cómo saberlo.
 *
 * <p>La dispersión viaja con la cifra a propósito. Una mediana sola vuelve a ser un número que
 * parece firme; con el rango delante, quien la lee sabe cuánto pesa.
 */
async function medir(url, etiqueta, puertoChrome, lighthouse, destino) {
  const corridas = [];
  for (let muestra = 1; muestra <= MUESTRAS; muestra++) {
    log(`  muestra ${muestra}/${MUESTRAS}`);
    corridas.push(await medirUnaVez(url, puertoChrome, lighthouse));
  }

  const ordenadas = [...corridas].sort((a, b) => a.puntajes.rendimiento - b.puntajes.rendimiento);
  const mediana = ordenadas[Math.floor((ordenadas.length - 1) / 2)];
  const peor = ordenadas[0].puntajes.rendimiento;
  const mejor = ordenadas.at(-1).puntajes.rendimiento;

  writeFileSync(join(destino, `${etiqueta}.json`), mediana.informe);

  return {
    fila: { pantalla: etiqueta, ...mediana.puntajes, "rendimiento (peor-mejor)": `${peor}-${mejor}` },
    detalle: {
      pantalla: etiqueta,
      dispersion: mejor - peor,
      muestras: corridas.map((corrida) => ({ ...corrida.puntajes, ...corrida.metricas })),
    },
  };
}

/**
 * Lo que se compara entre dos corridas, y en este orden.
 *
 * <p>No están las otras tres categorías —accesibilidad, buenas prácticas y SEO— porque salen del
 * DOM y no del reloj: dan lo mismo corrida tras corrida, y una tabla con tres filas que siempre
 * marcan "sin cambio" enseña a no leer la tabla.
 */
const COMPARABLES = [
  "rendimiento",
  "fcp",
  "lcp",
  "tbt",
  "eval. scripts",
  "estilo y layout",
  "peso kB",
  "fuentes kB",
];

/**
 * Cuánto se mueve cada métrica **sin que nadie cambie nada**, medido.
 *
 * <p>La primera versión de esta comparación usaba solo el solape de las bandas de cada corrida, y
 * el experimento de control la desmintió en el acto: se midió el mismo build dos veces, con cinco
 * minutos entre una y otra, y la tabla cantó como reales una caída de 198 ms en la evaluación de
 * scripts de la portada, 134 en estilo y layout, 107 en el TBT y 4 puntos de rendimiento. No
 * había cambiado una sola línea.
 *
 * <p>El motivo es que las tres muestras de una corrida son consecutivas: comparten el estado de
 * la máquina, así que su banda mide lo que varía en treinta segundos, no lo que varía entre dos
 * corridas separadas por un build. Por eso hace falta un piso, y por eso el piso son **cifras
 * observadas** —el peor movimiento de cada métrica en ese control, redondeado hacia arriba— y no
 * un porcentaje elegido a ojo.
 *
 * <p>Un piso no es una garantía: es el suelo por debajo del cual seguro que no se puede afirmar
 * nada. Por encima, la respuesta sigue siendo "repite el par", nunca "sí".
 */
const PISO = {
  rendimiento: 5,
  fcp: 10,
  lcp: 60,
  tbt: 110,
  "eval. scripts": 200,
  "estilo y layout": 140,
};

/** La mediana de una lista de números, ignorando los huecos. */
function medianaDe(valores) {
  const limpios = valores.filter((v) => typeof v === "number").sort((a, b) => a - b);
  if (limpios.length === 0) return null;
  return limpios[Math.floor((limpios.length - 1) / 2)];
}

function bandaDe(valores) {
  const limpios = valores.filter((v) => typeof v === "number");
  if (limpios.length === 0) return null;
  return [Math.min(...limpios), Math.max(...limpios)];
}

/**
 * Qué se puede decir de una diferencia. Nunca dice "sí" para una métrica de tiempo.
 *
 * <p>Con una sola pareja de corridas no hay forma de distinguir un cambio de un mal rato de la
 * máquina, y este arnés mide un build a la vez, así que ni siquiera puede intercalar A y B —que
 * es lo único que lo resolvería de verdad—. Lo honesto es decir hasta dónde llega: por debajo del
 * piso, nada; por encima, una candidata que hay que repetir.
 *
 * <p>El peso y las tipografías son la excepción, y no por ser importantes: son bytes. No dependen
 * del reloj ni de la máquina, así que ahí una diferencia es la diferencia.
 */
function veredicto(metrica, delta, seSolapan) {
  if (delta === 0) return "sin cambio";
  if (!(metrica in PISO)) return "sí: son bytes";
  if (seSolapan) return "no: dentro del ruido";
  if (Math.abs(delta) <= PISO[metrica]) return `no: bajo el piso (${PISO[metrica]})`;
  return "quizá: repite el par";
}

function leerResumen(etiqueta) {
  const ruta = join(carpetaDe(etiqueta), "resumen.json");
  if (!existsSync(ruta)) {
    const hay = etiquetasGuardadas();
    throw new Error(
      `No hay ninguna corrida guardada como '${etiqueta}' (falta ${ruta}).\n` +
        (hay.length
          ? `Guardadas ahora mismo: ${hay.join(", ")}.`
          : `No hay ninguna todavia. Mide con:  npm run lighthouse -- --etiqueta ${etiqueta}`),
    );
  }
  return JSON.parse(readFileSync(ruta, "utf8"));
}

/**
 * Compara dos corridas guardadas. No mide nada: no necesita ni API, ni build, ni Chrome.
 *
 * <p>Lo que distingue esto de restar dos números a mano es la última columna. Cada métrica se
 * compara **mediana contra mediana**, pero con la banda de sus propias muestras al lado: si las
 * dos bandas se solapan, la diferencia no se puede distinguir del ruido de la propia máquina y
 * así se dice. Es la regla que quedó escrita al cerrar la deuda 17 —"una diferencia menor que la
 * dispersión no es una mejora ni una regresión"— aplicada por la herramienta en vez de por la
 * buena memoria de quien lee la tabla.
 *
 * <p>El peso y los bytes de tipografía no se mueven entre muestras, así que ahí la banda es un
 * punto y cualquier diferencia es real: son bytes, no tiempos.
 */
function comparar(antes, despues) {
  const a = leerResumen(antes);
  const b = leerResumen(despues);

  log(`Comparando '${antes}' (${a.fecha}) con '${despues}' (${b.fecha}).`);
  log(`Muestras por pantalla: ${a.muestras} y ${b.muestras}.\n`);

  const pantallas = a.pantallas.map((p) => p.pantalla);
  const sobran = b.pantallas.filter((p) => !pantallas.includes(p.pantalla)).map((p) => p.pantalla);
  if (sobran.length) {
    log(`Aviso: '${despues}' trae pantallas que '${antes}' no tiene: ${sobran.join(", ")}.\n`);
  }

  for (const nombre of pantallas) {
    const pa = a.pantallas.find((p) => p.pantalla === nombre);
    const pb = b.pantallas.find((p) => p.pantalla === nombre);
    if (!pb) {
      log(`${nombre}: no está en '${despues}', se omite.\n`);
      continue;
    }
    const claves = Object.keys(pa.muestras[0] ?? {}).filter((clave) =>
      COMPARABLES.includes(clave),
    );
    const filas = [];
    for (const clave of claves) {
      const valoresA = pa.muestras.map((m) => m[clave]);
      const valoresB = pb.muestras.map((m) => m[clave]);
      const medA = medianaDe(valoresA);
      const medB = medianaDe(valoresB);
      if (medA === null || medB === null) continue;
      const bandaA = bandaDe(valoresA);
      const bandaB = bandaDe(valoresB);
      const seSolapan = bandaA[0] <= bandaB[1] && bandaB[0] <= bandaA[1];
      const delta = medB - medA;
      filas.push({
        metrica: clave,
        [antes]: `${medA}${bandaA[0] === bandaA[1] ? "" : ` (${bandaA[0]}-${bandaA[1]})`}`,
        [despues]: `${medB}${bandaB[0] === bandaB[1] ? "" : ` (${bandaB[0]}-${bandaB[1]})`}`,
        cambio: delta === 0 ? "=" : `${delta > 0 ? "+" : ""}${delta}`,
        "¿qué se puede decir?": veredicto(clave, delta, seSolapan),
      });
    }
    log(nombre);
    console.table(filas);
  }

  log(
    "\nComo leer la ultima columna:\n" +
      "  'dentro del ruido'   las muestras de las dos corridas se solapan.\n" +
      "  'bajo el piso'       se mueve menos que lo que se movio el MISMO build medido dos veces\n" +
      "                       (portada: 198 ms de evaluacion de scripts, 4 puntos, sin tocar nada).\n" +
      "  'quiza: repite'      es lo mas que se puede decir de un tiempo con una sola pareja de\n" +
      "                       corridas. Mide otra vez las dos y mira si el cambio se repite.\n" +
      "  'si: son bytes'      peso y tipografias no dependen del reloj.",
  );
}

async function main() {
  MUESTRAS = leerMuestras();
  const etiqueta = leerEtiqueta();

  const posicionComparar = process.argv.indexOf("--comparar");
  if (posicionComparar !== -1) {
    const [antes, despues] = process.argv.slice(posicionComparar + 1, posicionComparar + 3);
    if (!antes || !despues || antes.startsWith("--") || despues.startsWith("--")) {
      throw new Error(
        "--comparar pide dos etiquetas:  npm run lighthouse -- --comparar base fuentes\n" +
          (etiquetasGuardadas().length
            ? `Guardadas ahora mismo: ${etiquetasGuardadas().join(", ")}.`
            : "No hay ninguna corrida etiquetada todavia."),
      );
    }
    comparar(antes, despues);
    return;
  }

  if (!(await responde(`http://localhost:${PUERTO_API}/api/v1/salud`))) {
    throw new Error(
      `La API no responde en :${PUERTO_API}. Levanta 'docker compose up -d' y 'gradlew.bat bootRun' antes.`,
    );
  }

  if (!sinBuild) {
    log("> build de produccion");
    execSync("npm run build --workspace=apps/web", { cwd: RAIZ, stdio: "inherit", shell: true });
  }

  const proxy = levantarProxy();
  levantarSsr();
  await esperar(`${BASE}/es`, 60, "El servidor SSR");

  const slug = await primerSlug();
  const pantallas = [
    [`${BASE}/es`, "portada"],
    [`${BASE}/es/productos/${slug}`, "ficha"],
    [`${BASE}/es/legales/terminos`, "legales"],
  ];

  await exigirQueLaFichaCargue(pantallas[1][0]);
  const destino = carpetaDe(etiqueta);
  mkdirSync(destino, { recursive: true });

  const { default: lighthouse } = await import("lighthouse");
  const { launch } = await import("chrome-launcher");
  const chrome = await launch({ chromeFlags: ["--headless=new"] });

  const filas = [];
  const detalles = [];
  try {
    for (const [url, etiqueta] of pantallas) {
      log(`> midiendo ${etiqueta} (${MUESTRAS} ${MUESTRAS === 1 ? "muestra" : "muestras"})`);
      const medicion = await medir(url, etiqueta, chrome.port, lighthouse, destino);
      filas.push(medicion.fila);
      detalles.push(medicion.detalle);
    }
  } finally {
    await chrome.kill();
    proxy.close();
    for (const proceso of procesos) proceso.kill();
  }

  // Las muestras crudas quedan en disco: la dispersión de hoy es el único dato con el que la
  // medición de mañana se puede comparar sin volver a discutir si el arnés es confiable.
  const resumen = { fecha: new Date().toISOString(), muestras: MUESTRAS, pantallas: detalles };
  writeFileSync(
    join(destino, "resumen.json"),
    `${JSON.stringify(resumen, null, 2)}\n`,
  );

  console.table(filas);
  const donde = etiqueta ? `apps/web/lighthouse/${etiqueta}/` : "apps/web/lighthouse/";
  log(`\nInformes de la corrida mediana en ${donde}, las muestras en su resumen.json`);
  if (etiqueta) {
    log(`Para comparar con otra:  npm run lighthouse -- --comparar <otra> ${etiqueta}`);
  } else {
    // Decirlo aquí y no en el LEEME: quien acaba de medir sin etiqueta es exactamente quien está
    // a punto de medir otra vez y descubrir que el "antes" ya no existe.
    log(
      "Sin --etiqueta, la corrida siguiente pisa estos informes. Para medir un antes y un despues:\n" +
        "  npm run lighthouse -- --etiqueta base   ...y luego --etiqueta <lo-que-cambiaste>",
    );
  }

  if (MUESTRAS === 1) {
    log(
      "\nCorriste con --muestras 1: esto prueba el arnes, no mide el sitio. El rendimiento de una\n" +
        "sola muestra se ha movido hasta 22 puntos entre corridas del mismo build.",
    );
    return;
  }

  const inestables = detalles.filter((detalle) => detalle.dispersion >= 10);
  for (const { pantalla, dispersion } of inestables) {
    log(
      `\nAviso: en ${pantalla} las muestras de rendimiento se separan ${dispersion} puntos. La\n` +
        `mediana sigue siendo la mejor cifra disponible, pero una diferencia menor que eso frente a\n` +
        `otra medicion no es una mejora ni una regresion: es ruido.`,
    );
  }
}

main().catch((error) => {
  for (const proceso of procesos) proceso.kill();
  console.error(`\n${error.message}`);
  process.exit(1);
});
