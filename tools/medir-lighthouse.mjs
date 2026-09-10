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

async function medir(url, etiqueta, puertoChrome, lighthouse) {
  const resultado = await lighthouse(
    url,
    { port: puertoChrome, output: "json", logLevel: "error" },
    undefined,
  );
  const c = resultado.lhr.categories;
  const fila = {
    pantalla: etiqueta,
    rendimiento: Math.round(c.performance.score * 100),
    accesibilidad: Math.round(c.accessibility.score * 100),
    "buenas practicas": Math.round(c["best-practices"].score * 100),
    seo: Math.round(c.seo.score * 100),
  };
  const destino = join(RAIZ, "apps/web/lighthouse", `${etiqueta}.json`);
  writeFileSync(destino, resultado.report);
  return fila;
}

async function main() {
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
  try {
    for (const [url, etiqueta] of pantallas) {
      log(`> midiendo ${etiqueta}`);
      filas.push(await medir(url, etiqueta, chrome.port, lighthouse));
    }
  } finally {
    await chrome.kill();
    proxy.close();
    for (const proceso of procesos) proceso.kill();
  }

  console.table(filas);
  log("\nInformes completos en apps/web/lighthouse/");
  log(
    "Recuerda: mientras las imagenes de la siembra salgan de picsum.photos, el rendimiento de la\n" +
      "ficha no significa nada (docs/09-plan-de-arranque.md).",
  );
}

main().catch((error) => {
  for (const proceso of procesos) proceso.kill();
  console.error(`\n${error.message}`);
  process.exit(1);
});
