#!/usr/bin/env node
// ¿Usa la plantilla de los correos algún color que la marca no tiene?
//
// La regla dura #2 —ningún HEX suelto— la sostiene Tailwind en el frontend: las escalas por omisión
// están borradas, así que un color inventado no existe. Un correo no tiene Tailwind ni variables
// CSS —Gmail y Outlook no las leen—, así que la plantilla (adr/0098) escribe los colores en línea,
// en HEX, y aquí nada impedía que uno se desviara del kit. Este guardián lo impide: todo HEX de la
// plantilla y de los estilos que `PlantillaDeCorreo` le pone al contenido tiene que ser un valor de
// `color` en packages/marca/tokens.json. El modo oscuro queda fuera a propósito: la plantilla se
// declara solo clara (`color-scheme: light`).
import { readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));

const tokens = JSON.parse(readFileSync(join(RAIZ, "packages/marca/tokens.json"), "utf8"));
const permitidos = new Set(
  Object.entries(tokens.color)
    .filter(([clave, valor]) => !clave.startsWith("_") && typeof valor === "string")
    .map(([, valor]) => valor.toUpperCase()),
);

const ARCHIVOS = [
  "apps/api/infrastructure/src/main/resources/correo/plantilla.html",
  "apps/api/infrastructure/src/main/java/co/tecnosport/api/infrastructure/correo/PlantillaDeCorreo.java",
];

const problemas = [];
let vistos = 0;
for (const archivo of ARCHIVOS) {
  const ruta = join(RAIZ, archivo);
  readFileSync(ruta, "utf8")
    .split("\n")
    .forEach((linea, indice) => {
      for (const [hex] of linea.matchAll(/#[0-9A-Fa-f]{3,8}\b/g)) {
        vistos += 1;
        if (!permitidos.has(hex.toUpperCase())) {
          problemas.push(
            `${relative(RAIZ, ruta)}:${indice + 1}  ${hex} no es un color de tokens.json`,
          );
        }
      }
    });
}

// Un guardián que nunca ve nada da confianza falsa (la historia de eslint-plugin-boundaries en el
// CLAUDE.md): si la plantilla se mueve o deja de escribir HEX, esto tiene que fallar, no pasar.
if (vistos === 0) {
  problemas.push("No encontré ningún color en la plantilla de correo: ¿se movió el archivo?");
}

if (problemas.length > 0) {
  console.error("verificar-correo: colores fuera de la marca\n");
  for (const problema of problemas) console.error(`  ${problema}`);
  process.exit(1);
}
console.log(`verificar-correo: ${vistos} colores en la plantilla, todos de tokens.json.`);
