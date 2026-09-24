// Reproduce una grabación de `tools/sonda-nivel-360.mjs` contra el nivelador **real** y cuenta
// cuántas veces hablaría un lector de pantalla.
//
// Importa `nivel-360.ts` tal cual, sin copiarlo: lo que aquí se mide es lo que corre en el
// asistente. Node 22 lee el TypeScript directo, así que no hace falta compilar ni una dependencia
// nueva.
//
// **De dónde viene esta herramienta.** Se escribió para decidir la deuda 32 —cada cuánto anunciar
// el aviso de "obturador bloqueado" a un lector de pantalla— y acabó encontrando algo más gordo:
// el nivelador comparaba ángulos de Euler, y la pose de trabajo del asistente cae encima de su
// singularidad. Con el teléfono quieto bloqueaba el obturador el 69 % del tiempo, y en una vuelta
// completa a un producto, el 92 %. Esa medición está contada en la cabecera de `nivel-360.ts`, que
// ahora compara vectores de gravedad. Lo que queda por decidir con esta herramienta es lo que la
// motivó: cuánto retardo darle al anuncio.
//
// Uso:
//   npm run nivel-360                          la grabación más reciente
//   npm run nivel-360 -- --todas               todas las que haya, una detrás de otra
//   npm run nivel-360 -- --archivo <ruta>      una en concreto
//   npm run nivel-360 -- --histeresis 0,1,2 --retardo 0,500,1000
//
// No escribe nada y no necesita el teléfono: las grabaciones ya están en disco.

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import {
  evaluarNivel,
  gravedad,
  suavizar,
} from "../apps/web/src/app/features/captura360/domain/nivel-360.ts";

const RAIZ = new URL("..", import.meta.url).pathname.replace(
  /^\/([A-Za-z]:)/,
  "$1",
);
const GRABACIONES = join(RAIZ, "apps", "web", "nivel-360");

const HISTERESIS_POR_OMISION = [0, 1, 2, 3];
const RETARDOS_POR_OMISION = [0, 300, 600, 1000, 1500];
const REFERENCIA_MS = 1500;

function leerArgumentos() {
  const args = process.argv.slice(2);
  const valor = (nombre) => {
    const i = args.indexOf(`--${nombre}`);
    return i >= 0 ? args[i + 1] : undefined;
  };
  const lista = (nombre, omision) => {
    const crudo = valor(nombre);
    return crudo === undefined
      ? omision
      : crudo.split(",").map((n) => Number(n.trim()));
  };
  return {
    archivo: valor("archivo"),
    todas: args.includes("--todas"),
    histeresis: lista("histeresis", HISTERESIS_POR_OMISION),
    retardos: lista("retardo", RETARDOS_POR_OMISION),
  };
}

function grabaciones() {
  let archivos;
  try {
    archivos = readdirSync(GRABACIONES).filter((n) => n.endsWith(".json"));
  } catch {
    archivos = [];
  }
  if (archivos.length === 0) {
    console.error(`No hay ninguna grabación en ${GRABACIONES}.`);
    console.error("Primero: node tools/sonda-nivel-360.mjs, y grabar desde el teléfono.");
    process.exit(1);
  }
  return archivos
    .map((n) => join(GRABACIONES, n))
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
}

/**
 * Las grabaciones de la primera tanda traen `[t, beta, gamma]` y las de después
 * `[t, alpha, beta, gamma]`. Se distinguen por `campos`, y si falta, por el largo de la tupla.
 */
function normalizar(datos) {
  const conAlpha = Array.isArray(datos.campos)
    ? datos.campos.includes("alpha")
    : (datos.muestras?.[0]?.length ?? 3) >= 4;

  return (datos.muestras ?? [])
    .map((fila) =>
      conAlpha
        ? { t: fila[0], alpha: fila[1], beta: fila[2], gamma: fila[3] }
        : { t: fila[0], alpha: null, beta: fila[1], gamma: fila[2] },
    )
    .filter(({ beta, gamma }) => beta !== null && gamma !== null);
}

/**
 * La clave del mensaje que pinta `ts-indicador-nivel.ts`. Va copiada aquí y no importada porque
 * vive en la capa de presentación, que esta herramienta no puede importar.
 */
function mensajeDe(nivel) {
  if (nivel.estado === "SIN_SENSOR") return { clave: "sin_sensor", grados: 0 };
  if (nivel.estado === "EN_RANGO") return { clave: "en_rango", grados: 0 };

  const valor = nivel.ejeDominante === "GIRAR" ? nivel.girar : nivel.inclinar;
  const clave =
    nivel.ejeDominante === "GIRAR"
      ? valor > 0
        ? "gira_derecha"
        : "gira_izquierda"
      : valor > 0
        ? "inclina_atras"
        : "inclina_adelante";
  return { clave, grados: Math.abs(Math.round(valor)) };
}

/** Reproduce la grabación entera, lectura a lectura, como la recibiría el asistente. */
function reproducir(lecturas, histeresis) {
  const paso = [];
  let suavizada = null;
  let referencia = null;
  let anterior = null;

  for (const { t, beta, gamma } of lecturas) {
    suavizada = suavizar(suavizada, { beta, gamma });
    // La referencia la fija la primera toma. Aquí, el primer segundo y medio: el guion de la
    // sonda pide arrancar ya en la pose y aguantar antes de moverse.
    if (referencia === null && t - lecturas[0].t >= REFERENCIA_MS) {
      referencia = suavizada;
    }
    const nivel = evaluarNivel(suavizada, referencia, anterior, {
      histeresisGrados: histeresis,
    });
    anterior = nivel.estado;
    if (referencia !== null) {
      paso.push({ t, nivel, mensaje: mensajeDe(nivel) });
    }
  }
  return paso;
}

/**
 * Cuántas veces cambiaría el texto anunciado, exigiendo que el valor nuevo se sostenga
 * `retardoMs` antes de contar, y el **desajuste**: la fracción del tiempo en que lo anunciado no
 * es lo que el nivel dice en ese instante. Un retardo largo deja la región casi muda, y sin ese
 * número la mudez parecería un logro.
 */
function anuncios(paso, textoDe, retardoMs) {
  let anunciado = null;
  let candidato = null;
  let desde = 0;
  let cuenta = 0;
  let desajustado = 0;
  let anteriorT = paso[0].t;

  for (const punto of paso) {
    const texto = textoDe(punto);
    if (texto !== candidato) {
      candidato = texto;
      desde = punto.t;
    }
    if (candidato !== anunciado && punto.t - desde >= retardoMs) {
      anunciado = candidato;
      cuenta += 1;
    }
    if (anunciado !== texto) desajustado += punto.t - anteriorT;
    anteriorT = punto.t;
  }

  const total = paso.at(-1).t - paso[0].t;
  return { cuenta, desajuste: total > 0 ? desajustado / total : 0 };
}

function cambiosDeObturador(paso) {
  let anterior = null;
  let cuenta = 0;
  for (const { nivel } of paso) {
    if (anterior !== null && nivel.puedeDisparar !== anterior) cuenta += 1;
    anterior = nivel.puedeDisparar;
  }
  return cuenta;
}

function estadistica(valores) {
  const media = valores.reduce((s, x) => s + x, 0) / valores.length;
  const sd = Math.sqrt(
    valores.reduce((s, x) => s + (x - media) ** 2, 0) / valores.length,
  );
  return { media, sd, maximo: Math.max(...valores) };
}

/**
 * Cuánto barrió la brújula: distingue una vuelta alrededor del producto de estar parado.
 *
 * **No es la suma de los saltos.** `alpha` se degenera en la misma singularidad que `gamma` —a
 * `beta` cerca de 90° las dos se acoplan—, así que sumar valores absolutos cuenta el ruido: la
 * primera versión de esta función informó 14.833°, o sea 41 vueltas, sobre una grabación en la
 * que el teléfono dio una. Lo que sí sobrevive al ruido es la **tendencia**: se desenrolla, se
 * promedia por segundo y se mide el recorrido de esa curva suave.
 */
function recorridoDeBrujula(lecturas) {
  let desenrollado = lecturas[0].alpha;
  let anterior = lecturas[0].alpha;
  const porSegundo = [];
  let cubo = [];
  let segundo = 0;

  for (const { t, alpha } of lecturas) {
    let d = alpha - anterior;
    while (d > 180) d -= 360;
    while (d < -180) d += 360;
    desenrollado += d;
    anterior = alpha;
    cubo.push(desenrollado);
    if ((t - lecturas[0].t) / 1000 >= segundo + 1) {
      porSegundo.push(cubo.reduce((a, b) => a + b, 0) / cubo.length);
      cubo = [];
      segundo++;
    }
  }
  if (cubo.length > 0) porSegundo.push(cubo.reduce((a, b) => a + b, 0) / cubo.length);
  if (porSegundo.length < 2) return 0;
  return Math.max(...porSegundo) - Math.min(...porSegundo);
}

function analizar(ruta, { histeresis, retardos }) {
  const datos = JSON.parse(readFileSync(ruta, "utf8"));
  const lecturas = normalizar(datos);

  console.log("");
  console.log("=".repeat(78));
  console.log(ruta.split(/[\\/]/).at(-1));

  const paso = lecturas.length >= 2 ? reproducir(lecturas, undefined) : [];
  if (paso.length < 2) {
    console.log("  demasiado corta: no alcanza ni a fijar la referencia.");
    console.log("");
    return;
  }

  const segundos = (lecturas.at(-1).t - lecturas[0].t) / 1000;
  const minutos = (paso.at(-1).t - paso[0].t) / 60000;
  const porMinuto = (n) => (n / minutos).toFixed(1);

  console.log(
    `  ${lecturas.length} lecturas en ${segundos.toFixed(1)} s ` +
      `(~${(lecturas.length / segundos).toFixed(0)} Hz)`,
  );
  console.log(
    lecturas[0].alpha !== null
      ? `  la brújula barrió ${recorridoDeBrujula(lecturas).toFixed(0)}° (tendencia, no ruido)`
      : "  sin brújula: grabación de la primera tanda, no distingue giro de quietud",
  );
  console.log("");

  const desv = estadistica(paso.map((p) => p.nivel.desviacion));
  const bloqueado =
    (paso.filter(({ nivel }) => !nivel.puedeDisparar).length / paso.length) * 100;

  console.log(`  desviación   media ${desv.media.toFixed(2)}° · sd ${desv.sd.toFixed(2)}° · máximo ${desv.maximo.toFixed(2)}°`);
  console.log(`  bloqueado    ${bloqueado.toFixed(0)} % del tiempo`);
  console.log(
    `  el indicador habla ${porMinuto(anuncios(paso, ({ mensaje }) => `${mensaje.clave}:${mensaje.grados}`, 0).cuenta)} veces por minuto, con los grados dentro del texto`,
  );
  console.log("");
  console.log("  Anuncios por minuto (% del tiempo desajustado), sin los grados en el texto:");
  console.log("");
  process.stdout.write("    hist.  obtur./min ");
  for (const retardo of retardos) process.stdout.write(`${retardo} ms`.padStart(16));
  console.log("");

  for (const h of histeresis) {
    const pasoH = reproducir(lecturas, h);
    process.stdout.write(
      `    ${`${h}°`.padStart(5)}  ${porMinuto(cambiosDeObturador(pasoH)).padStart(10)} `,
    );
    for (const retardo of retardos) {
      const r = anuncios(pasoH, ({ mensaje }) => mensaje.clave, retardo);
      process.stdout.write(
        `${`${porMinuto(r.cuenta)} (${(r.desajuste * 100).toFixed(0)}%)`.padStart(16)}`,
      );
    }
    console.log("");
  }
  console.log("");
}

const opciones = leerArgumentos();
const rutas = opciones.archivo
  ? [opciones.archivo]
  : opciones.todas
    ? grabaciones()
    : [grabaciones()[0]];

for (const ruta of rutas) analizar(ruta, opciones);

console.log("La columna `obtur./min` no depende del retardo: es el parpadeo del botón, y solo");
console.log("la histéresis lo arregla. El porcentaje entre paréntesis es cuánto tiempo lo dicho");
console.log("no coincide con lo que pasa; un retardo que calla mucho lo paga ahí.");
console.log("");
