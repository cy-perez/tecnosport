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
// Requiere PostgreSQL y la API arriba:
//   docker compose up -d && (cd apps/api && gradlew.bat bootRun)
import { execSync } from "node:child_process";
import { spawn } from "node:child_process";
import { createServer, request as pedir } from "node:http";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const PUERTO_API = 8080;
const PUERTO_SSR = 4000;
const PUERTO_PROXY = 4300;
const BASE = `http://localhost:${PUERTO_PROXY}`;

const sinBuild = process.argv.includes("--sin-build");
const procesos = [];

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
    informe: resultado.report,
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
async function medir(url, etiqueta, puertoChrome, lighthouse) {
  const corridas = [];
  for (let muestra = 1; muestra <= MUESTRAS; muestra++) {
    log(`  muestra ${muestra}/${MUESTRAS}`);
    corridas.push(await medirUnaVez(url, puertoChrome, lighthouse));
  }

  const ordenadas = [...corridas].sort((a, b) => a.puntajes.rendimiento - b.puntajes.rendimiento);
  const mediana = ordenadas[Math.floor((ordenadas.length - 1) / 2)];
  const peor = ordenadas[0].puntajes.rendimiento;
  const mejor = ordenadas.at(-1).puntajes.rendimiento;

  writeFileSync(join(RAIZ, "apps/web/lighthouse", `${etiqueta}.json`), mediana.informe);

  return {
    fila: { pantalla: etiqueta, ...mediana.puntajes, "rendimiento (peor-mejor)": `${peor}-${mejor}` },
    detalle: {
      pantalla: etiqueta,
      dispersion: mejor - peor,
      muestras: corridas.map((corrida) => corrida.puntajes),
    },
  };
}

async function main() {
  MUESTRAS = leerMuestras();

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
  mkdirSync(join(RAIZ, "apps/web/lighthouse"), { recursive: true });

  const { default: lighthouse } = await import("lighthouse");
  const { launch } = await import("chrome-launcher");
  const chrome = await launch({ chromeFlags: ["--headless=new"] });

  const filas = [];
  const detalles = [];
  try {
    for (const [url, etiqueta] of pantallas) {
      log(`> midiendo ${etiqueta} (${MUESTRAS} ${MUESTRAS === 1 ? "muestra" : "muestras"})`);
      const medicion = await medir(url, etiqueta, chrome.port, lighthouse);
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
    join(RAIZ, "apps/web/lighthouse", "resumen.json"),
    `${JSON.stringify(resumen, null, 2)}\n`,
  );

  console.table(filas);
  log("\nInformes de la corrida mediana en apps/web/lighthouse/, las muestras en resumen.json");

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
