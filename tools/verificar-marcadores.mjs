#!/usr/bin/env node
// ¿Quedó algún marcador [[ ]] en un texto que se publica?
//
// Un marcador es una anotación para quien audita: sirve para no inventar un dato que falta. Lo que
// no puede es llegar a la pantalla. Los documentos legales de este sitio publicaron seis durante
// una fase entera —"el plazo de entrega es de [[PLAZO DE ENTREGA REAL]] días calendario"— y nada
// falló, porque la plantilla pinta la clave de Transloco tal como está y ninguna prueba mira el
// contenido del texto. Un documento así informa peor que uno que no prometa nada, y encima deja
// por escrito que el comerciante sabía que le faltaba el dato.
//
// Solo mira `src/assets/i18n`: los marcadores en comentarios de código son correctos y útiles, y
// `dist/` es salida de compilación. Ver .claude/skills/vacios-legales-del-sitio/references/
// cerrar-marcadores.md para qué hacer con uno cuando aparece — no se borra, se cierra.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const TEXTOS = join(RAIZ, "apps/web/src/assets/i18n");
const MARCADOR = /\[\[[^\]\n]+\]\]/g;

function jsons(directorio) {
  return readdirSync(directorio).flatMap((entrada) => {
    const ruta = join(directorio, entrada);
    if (statSync(ruta).isDirectory()) {
      return jsons(ruta);
    }
    return ruta.endsWith(".json") ? [ruta] : [];
  });
}

const hallazgos = [];
for (const ruta of jsons(TEXTOS)) {
  readFileSync(ruta, "utf8")
    .split("\n")
    .forEach((linea, indice) => {
      for (const encontrado of linea.match(MARCADOR) ?? []) {
        hallazgos.push({ ruta: relative(RAIZ, ruta), linea: indice + 1, encontrado });
      }
    });
}

if (hallazgos.length > 0) {
  console.error(
    `\n${hallazgos.length} marcador(es) sin cerrar en textos que se publican:\n`,
  );
  for (const { ruta, linea, encontrado } of hallazgos) {
    console.error(`  ${ruta}:${linea}  ${encontrado}`);
  }
  console.error(
    "\nUn marcador no se publica. Ciérralo con una de las dos salidas: quitar la promesa\n" +
      "concreta, o declarar el mínimo legal diciendo que es el legal. Nunca inventando un valor\n" +
      "plausible. Guía: .claude/skills/vacios-legales-del-sitio/references/cerrar-marcadores.md\n",
  );
  process.exit(1);
}

console.log("Textos publicados sin marcadores pendientes.");
