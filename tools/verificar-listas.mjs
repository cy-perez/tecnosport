#!/usr/bin/env node
// ¿El parser de listas de proveedor sigue leyendo las listas como las leía?
//
// La skill `listas-de-proveedor` convierte en productos los mensajes de WhatsApp del proveedor de
// tecnología, y hasta el 8 de octubre de 2026 no tenía ni una prueba. Ese día llegó una lista con
// 🎃 de viñeta y el parser perdió 52 líneas —23 equipos publicables— sin un solo error: quedaron
// en "sin clasificar", al final de un reporte de 360 líneas.
//
// Las pruebas viven con la skill (`pruebas/`) y usan solo la librería estándar. Este archivo las
// engancha a `npm run verificar`, con el mismo criterio que el guardián del kit: en integración
// continua, sin Python, falla; en local avisa y sigue.
//
// Uso:  node tools/verificar-listas.mjs
import { spawnSync } from "node:child_process";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { buscarPython } from "./buscar-python.mjs";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
// Las dos skills que arman el catálogo de tecnología. La de fotos solo prueba aquí lo que
// no pide OpenCV: preparar las carpetas de modelo y devolver los resultados a ellas.
const PRUEBAS = [
  join(RAIZ, ".claude/skills/listas-de-proveedor/pruebas"),
  join(RAIZ, ".claude/skills/fotos-estudio-degradado/pruebas"),
];

const python = buscarPython();
if (!python) {
  const mensaje =
    "No hay Python. El parser de listas de proveedor es Python, así que sin intérprete no se" +
    " pueden correr sus pruebas.";
  if (process.env.CI) {
    console.error(`ERROR: ${mensaje}`);
    process.exit(1);
  }
  console.log(`AVISO: ${mensaje}\n       Se omiten las pruebas de la skill.`);
  process.exit(0);
}

// `-B` para no dejar __pycache__ junto a los scripts de la skill en cada corrida.
let estado = 0;
for (const carpeta of PRUEBAS) {
  const resultado = spawnSync(python, ["-B", "-m", "unittest", "discover", "-s", carpeta], {
    stdio: "inherit",
    env: { ...process.env, PYTHONIOENCODING: "utf-8" },
  });
  if (resultado.status !== 0) estado = resultado.status ?? 1;
}
process.exit(estado);
