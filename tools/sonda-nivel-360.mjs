// Sonda del nivelador del asistente 360: **graba el sensor, no decide nada**.
//
// La pregunta que la motiva es la deuda 32 del plan: el aviso de "obturador bloqueado" cuelga del
// acelerómetro, y cuál de las salidas sirve "depende de cómo se comporta el nivel de verdad, con
// la mano temblando y el teléfono girando". Eso es un dato que solo tiene el teléfono.
//
// Esta sonda graba las lecturas **crudas** de `DeviceOrientationEvent` y las guarda en el
// computador. No aplica el suavizado ni evalúa el nivel: de eso se encarga
// `tools/analizar-nivel-360.mjs`, que reproduce la grabación contra el `nivel-360.ts` real. La
// separación es el punto — se graba una vez y se reproduce con cuantos candidatos haga falta, en
// vez de volver al teléfono por cada número que se quiera probar.
//
// No toca la aplicación: no necesita cámara, ni backend, ni docker, ni sesión de administrador.
//
// Uso:
//   node tools/sonda-nivel-360.mjs              sirve la página en :4300
//   PUERTO=4301 node tools/sonda-nivel-360.mjs  en otro puerto
//
// Y desde el teléfono, porque el sensor solo existe en contexto seguro (`apps/web/README.md`):
//   cloudflared tunnel --url http://localhost:4300
//
// Las grabaciones caen en `apps/web/nivel-360/`, que está fuera del repositorio como las de
// Lighthouse: son datos de una corrida, no fuente.

import { mkdirSync, writeFileSync } from "node:fs";
import http from "node:http";
import { join } from "node:path";

const PUERTO = Number(process.env.PUERTO ?? 4300);
const RAIZ = new URL("..", import.meta.url).pathname.replace(
  /^\/([A-Za-z]:)/,
  "$1",
);
const DESTINO = join(RAIZ, "apps", "web", "nivel-360");

const PAGINA = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Sonda del nivel 360</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; padding: 16px; background: #14161a; color: #f2f4f7;
         font: 16px/1.4 system-ui, sans-serif; }
  h1 { font-size: 18px; margin: 0 0 12px; }
  p { margin: 0 0 12px; }
  button { display: block; width: 100%; min-height: 56px; margin: 0 0 12px;
           font: inherit; font-weight: 600; border: 0; border-radius: 4px;
           background: #2f6df6; color: #fff; }
  button:disabled { background: #3a3f47; color: #9aa1ab; }
  .secundario { background: #3a3f47; }
  .lectura { font-variant-numeric: tabular-nums; font-size: 28px; font-weight: 700;
             margin: 0 0 12px; }
  .estado { min-height: 48px; padding: 8px 12px; border-radius: 4px; background: #1e2127; }
  .mal { background: #4a1f22; }
  .bien { background: #1f4a2b; }
  fieldset { border: 1px solid #3a3f47; border-radius: 4px; margin: 0 0 12px; }
  legend { padding: 0 6px; font-size: 14px; color: #9aa1ab; }
  label { display: block; margin: 0 0 8px; }
  input { width: 100%; box-sizing: border-box; min-height: 44px; padding: 0 8px;
          font: inherit; border-radius: 4px; border: 1px solid #3a3f47;
          background: #1e2127; color: inherit; }
</style>
</head>
<body>
<h1>Sonda del nivel 360</h1>

<p class="lectura" id="lectura">b — · g — · a —</p>

<div class="estado" id="estado">Sin arrancar.</div>

<fieldset>
  <legend>Qué se está grabando</legend>
  <label>Nombre <input id="nombre" value="quieto" autocomplete="off" /></label>
  <button class="secundario" data-nombre="quieto">Apuntar quieto, 60 s</button>
  <button class="secundario" data-nombre="set-8">Un set de 8, dando la vuelta</button>
</fieldset>

<button id="permiso">1 · Activar el sensor</button>
<button id="grabar" disabled>2 · Grabar</button>
<button id="parar" disabled>3 · Parar y guardar</button>

<script>
const $ = (id) => document.getElementById(id);
const estado = $("estado");
let muestras = [];
let grabando = false;
let arranque = 0;
let bloqueo = null;

function decir(texto, clase) {
  estado.textContent = texto;
  estado.className = "estado" + (clase ? " " + clase : "");
}

for (const boton of document.querySelectorAll("[data-nombre]")) {
  boton.addEventListener("click", () => { $("nombre").value = boton.dataset.nombre; });
}

function alLeer(evento) {
  const alpha = evento.alpha;
  const beta = evento.beta;
  const gamma = evento.gamma;
  const n = (v) => (v === null || v === undefined ? "—" : v.toFixed(1));
  $("lectura").textContent =
    "b " + n(beta) + " · g " + n(gamma) + " · a " + n(alpha);

  if (!grabando) return;
  // alpha es la brujula, y el nivelador no la usa a proposito: girar alrededor del producto es
  // justo lo que se quiere. Se graba de todos modos porque sin ella no se puede distinguir una
  // vuelta de verdad de estar parado, y esa distincion es la que tiene que demostrar el recorrido.
  // (Sin acentos graves aqui dentro: este comentario vive en la plantilla literal de la pagina.)
  muestras.push([
    Math.round(performance.now() - arranque),
    alpha === null || alpha === undefined ? null : Number(alpha.toFixed(3)),
    beta === null ? null : Number(beta.toFixed(3)),
    gamma === null ? null : Number(gamma.toFixed(3)),
  ]);
  const segundos = (muestras.length && muestras[muestras.length - 1][0] / 1000) || 0;
  decir("Grabando… " + muestras.length + " lecturas en " + segundos.toFixed(0) + " s.");
}

$("permiso").addEventListener("click", async () => {
  if (typeof window.DeviceOrientationEvent === "undefined") {
    decir("Este navegador no expone DeviceOrientationEvent. Sin sensor no hay nada que grabar.", "mal");
    return;
  }
  // En Android la función no existe y basta suscribirse; en iOS hay que pedirlo desde el gesto.
  const pedir = window.DeviceOrientationEvent.requestPermission;
  if (typeof pedir === "function") {
    try {
      if ((await pedir.call(window.DeviceOrientationEvent)) !== "granted") {
        decir("Permiso negado. Sin él no llega ninguna lectura.", "mal");
        return;
      }
    } catch (e) {
      decir("El permiso falló: " + e + ". Fuera de HTTPS iOS lo rechaza siempre.", "mal");
      return;
    }
  }
  window.addEventListener("deviceorientation", alLeer);
  $("permiso").disabled = true;
  $("grabar").disabled = false;
  decir("Sensor escuchando. Mira que los números de arriba se muevan antes de grabar.", "bien");
});

$("grabar").addEventListener("click", async () => {
  muestras = [];
  arranque = performance.now();
  grabando = true;
  $("grabar").disabled = true;
  $("parar").disabled = false;
  // Sin esto la pantalla se apaga a mitad de los 60 segundos y el sensor deja de entregar.
  try { bloqueo = await navigator.wakeLock?.request("screen"); } catch { bloqueo = null; }
  decir("Grabando…");
});

$("parar").addEventListener("click", async () => {
  grabando = false;
  $("parar").disabled = true;
  try { await bloqueo?.release(); } catch { /* da igual: ya se soltó */ }
  bloqueo = null;

  if (muestras.length === 0) {
    decir("Ninguna lectura llegó. El sensor está escuchando pero no entrega: mira los números.", "mal");
    $("grabar").disabled = false;
    return;
  }

  decir("Guardando " + muestras.length + " lecturas…");
  try {
    const respuesta = await fetch("/grabacion", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        nombre: $("nombre").value,
        agente: navigator.userAgent,
        campos: ["t", "alpha", "beta", "gamma"],
        muestras,
      }),
    });
    const cuerpo = await respuesta.json();
    if (!respuesta.ok) throw new Error(cuerpo.error ?? respuesta.status);
    decir("Guardado en " + cuerpo.archivo + " · " + muestras.length + " lecturas.", "bien");
  } catch (e) {
    decir("No se pudo guardar: " + e + ". El computador tiene que seguir sirviendo la sonda.", "mal");
  }
  $("grabar").disabled = false;
});
</script>
</body>
</html>
`;

function guardar(cuerpo, res) {
  let datos;
  try {
    datos = JSON.parse(cuerpo);
  } catch {
    res.writeHead(400, { "content-type": "application/json" });
    return res.end(JSON.stringify({ error: "cuerpo ilegible" }));
  }

  const nombre = String(datos.nombre ?? "sin-nombre")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9-]+/g, "-")
    .replace(/^-|-$/g, "")
    .toLowerCase();
  // Hora local y no UTC: en Medellin una grabacion de la noche quedaria fechada manana.
  const ahora = new Date();
  const dos = (n) => String(n).padStart(2, "0");
  const sello =
    ahora.getFullYear() +
    "-" + dos(ahora.getMonth() + 1) +
    "-" + dos(ahora.getDate()) +
    "-" + dos(ahora.getHours()) +
    "-" + dos(ahora.getMinutes()) +
    "-" + dos(ahora.getSeconds());
  const archivo = join(DESTINO, `${nombre || "sin-nombre"}-${sello}.json`);

  mkdirSync(DESTINO, { recursive: true });
  writeFileSync(archivo, JSON.stringify(datos, null, 1));
  const segundos = (datos.muestras?.at(-1)?.[0] ?? 0) / 1000;
  const hz = segundos > 0 ? (datos.muestras.length / segundos).toFixed(1) : "?";
  console.log(
    `  ${archivo}\n  ${datos.muestras?.length ?? 0} lecturas en ${segundos.toFixed(1)} s (~${hz} Hz)`,
  );

  res.writeHead(200, { "content-type": "application/json" });
  res.end(JSON.stringify({ archivo }));
}

http
  .createServer((req, res) => {
    if (req.method === "POST" && req.url === "/grabacion") {
      let cuerpo = "";
      req.on("data", (trozo) => (cuerpo += trozo));
      req.on("end", () => guardar(cuerpo, res));
      return;
    }
    if (req.method === "GET" && (req.url === "/" || req.url.startsWith("/?"))) {
      res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
      return res.end(PAGINA);
    }
    res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    res.end("Solo / y POST /grabacion.\n");
  })
  .listen(PUERTO, () => {
    console.log(`Sonda del nivel 360 en http://localhost:${PUERTO}`);
    console.log(`Grabaciones en ${DESTINO}`);
    console.log("");
    console.log("Desde el teléfono hace falta contexto seguro:");
    console.log(`  cloudflared tunnel --url http://localhost:${PUERTO}`);
  });
