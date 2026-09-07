#!/usr/bin/env node
// Verifica la dirección de las dependencias en el frontend.
//
// Existe porque `eslint-plugin-boundaries` **no verificaba nada**: la
// configuración legada la acepta el plugin v7 y no la aplica, y su
// clasificación de elementos tampoco funciona sin `mode: 'full'`. Se comprobó
// metiendo violaciones a propósito y el lint pasaba. Un guardián que nunca
// dispara es peor que no tener guardián, porque da confianza falsa.
//
// Esto no reemplaza al plugin en general: solo aplica las cuatro reglas que
// este proyecto necesita, sobre los imports relativos entre capas. Es corto a
// propósito — se lee entero en un minuto y se prueba metiendo una violación.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, resolve, dirname, sep } from "node:path";

const RAIZ = "apps/web/src/app";

// Dos excepciones, y las dos están en el propio enunciado de las reglas.
//
// `*.routes.ts`: el mensaje de la regla dice literalmente que "el proveedor de
// la ruta decide la implementación de infrastructure". Ese proveedor vive en el
// archivo de rutas, así que prohibírselo era prohibir justo lo que la regla
// manda hacer.
//
// `*.spec.ts`: una prueba monta el escenario, y eso incluye sembrar el
// almacenamiento local o inyectar un adaptador. No es código que se despliegue.
// Se informan aparte para que no desaparezcan de la vista.
const ES_RUTAS = /\.routes\.ts$/;
const ES_PRUEBA = /\.spec\.ts$/;

/** A qué capa pertenece un archivo, por su ruta. */
function capaDe(rutaRelativa) {
  const p = rutaRelativa.split(sep).join("/");
  const m = p.match(/^features\/[^/]+\/(domain|application|infrastructure|presentation)\//);
  if (m) return m[1];
  if (p.startsWith("shared/")) return "shared";
  return null; // core, layout, la raíz: fuera del grafo, como antes
}

// Qué NO puede importar cada capa. Mismas reglas que `eslint.config.js`
// declaraba y no aplicaba.
const PROHIBIDO = {
  domain: {
    capas: ["application", "infrastructure", "presentation"],
    motivo: "domain no depende de nada. Sin HttpClient, sin Angular.",
  },
  application: {
    capas: ["infrastructure", "presentation"],
    motivo: "application solo depende de domain (los puertos, no sus adaptadores).",
  },
  presentation: {
    capas: ["infrastructure"],
    motivo:
      "presentation inyecta el puerto declarado en domain; el proveedor de la ruta decide la implementación de infrastructure.",
  },
  shared: {
    capas: ["domain", "application", "infrastructure", "presentation"],
    motivo:
      "shared/ no depende de ninguna funcionalidad. Si solo lo usa una, el componente va dentro de ella; si es compartido de verdad, recibe entradas primitivas en vez del tipo del dominio.",
  },
};

function* archivosTs(dir) {
  for (const entrada of readdirSync(dir)) {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) yield* archivosTs(ruta);
    else if (entrada.endsWith(".ts")) yield ruta;
  }
}

const IMPORT = /(?:^|\n)\s*(?:import|export)\s[^;]*?from\s+['"](\.[^'"]+)['"]/g;

const violaciones = [];
const enPruebas = [];
for (const archivo of archivosTs(RAIZ)) {
  const rel = relative(RAIZ, archivo);
  const desde = capaDe(rel);
  const regla = desde && PROHIBIDO[desde];
  if (!regla) continue;
  if (ES_RUTAS.test(archivo)) continue;

  const fuente = readFileSync(archivo, "utf8");
  for (const m of fuente.matchAll(IMPORT)) {
    const destino = capaDe(relative(RAIZ, resolve(dirname(archivo), m[1])));
    if (destino && regla.capas.includes(destino)) {
      (ES_PRUEBA.test(archivo) ? enPruebas : violaciones).push({
        archivo: relative(".", archivo).split(sep).join("/"),
        desde,
        destino,
        especificador: m[1],
        motivo: regla.motivo,
      });
    }
  }
}

if (enPruebas.length > 0) {
  console.log(`aviso: ${enPruebas.length} import(s) de este tipo en archivos de prueba, que no fallan el build:`);
  for (const v of enPruebas) {
    console.log(`  ${v.archivo}  ${v.desde} -> ${v.destino} (${v.especificador})`);
  }
  console.log("");
}

if (violaciones.length === 0) {
  console.log("capas: ninguna dependencia invertida en codigo de produccion");
  process.exit(0);
}

for (const v of violaciones) {
  console.log(`${v.archivo}\n  ${v.desde} -> ${v.destino}  (${v.especificador})\n  ${v.motivo}\n`);
}
console.log(`dependencias invertidas: ${violaciones.length}`);
process.exit(1);
