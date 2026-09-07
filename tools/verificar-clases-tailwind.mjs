#!/usr/bin/env node
// Comprueba que una clase de Tailwind exista de verdad en este proyecto.
//
// Existe porque las escalas por omisión de Tailwind están borradas
// (`apps/web/src/tailwind.css`, ADR-0020) y **una clase que no existe no falla:
// simplemente no hace nada**. No hay linter que avise. Pasó con `min-h-0` y
// `min-h-auto`.
//
//   node tools/verificar-clases-tailwind.mjs min-h-tactil bg-ts-primario
//
// Dos detalles que costaron caro y por eso están resueltos aquí:
//
//  1. `@source inline(...)`. Tailwind solo genera las clases que **encuentra**
//     en el código, así que preguntar por una clase nueva sin forzarla como
//     candidato siempre respondería "no existe". Con esto se comprueba si la
//     clase es válida, no si además ya se usa.
//  2. El escapado. En el CSS generado, `focus-visible:outline-2` es el selector
//     `.focus-visible\:outline-2`. Escapar de menos —solo los dos puntos, o
//     olvidarse de la barra de `bg-ts-marca-fuerte/60`— da falsos negativos.
//     Se escapa todo lo que no sea alfanumérico.
import postcss from "postcss";
import tailwind from "@tailwindcss/postcss";
import { readFileSync } from "node:fs";

const clases = process.argv.slice(2);
if (clases.length === 0) {
  console.error("Uso: node tools/verificar-clases-tailwind.mjs <clase> [clase...]");
  process.exit(2);
}

const ruta = "apps/web/src/tailwind.css";
const entrada = `${readFileSync(ruta, "utf8")}\n@source inline("${clases.join(" ")}");\n`;
const { css } = await postcss([tailwind()]).process(entrada, { from: ruta });

const BARRA = String.fromCharCode(92);
const escapar = (clase) => clase.replace(/[^a-zA-Z0-9_-]/g, (caracter) => BARRA + caracter);

const faltan = clases.filter((clase) => !css.includes(`.${escapar(clase)}`));

console.log(`comprobadas ${clases.length}`);
if (faltan.length > 0) {
  console.log(`NO EXISTEN (no hacen nada): ${faltan.join(" ")}`);
  process.exit(1);
}
console.log("todas existen");
