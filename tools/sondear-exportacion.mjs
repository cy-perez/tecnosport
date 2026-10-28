#!/usr/bin/env node
// ¿Cómo quedaría la ingesta de una exportación de WhatsApp, antes de subirla al panel?
//
// Pasa el zip por el mismo código de la API —el lector del zip, el agrupador y las reglas de texto
// (precio, ajuste de talla, género, réplica, acompañante, calzado que no es tenis)— y escribe cada
// publicación con su texto, sus fotos y lo que esas reglas leen de ella, más los mensajes que
// quedan sueltos. No llama a Claude: lo que leerían el extractor y el lector de fotos no sale aquí.
//
// Nació el 28 de octubre de 2026, de los análisis previos a las ingestas de ese día, que hasta
// entonces se hacían con una prueba temporal que se borraba. La prueba vive en
// `SondaDeExportacionTest` (infraestructura) y no corre en el build.
//
// Uso:  npm run sondear -- <zip o carpeta de zips> [--salida archivo.txt]
// Por omisión escribe en apps/api/infrastructure/build/sonda/<nombre>.txt.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const API = join(RAIZ, "apps/api");

const argumentos = process.argv.slice(2);
const indiceSalida = argumentos.indexOf("--salida");
const salidaPedida = indiceSalida >= 0 ? argumentos[indiceSalida + 1] : null;
const origen = argumentos.find(
  (a, i) => !a.startsWith("--") && (indiceSalida < 0 || i !== indiceSalida + 1),
);

if (!origen || (indiceSalida >= 0 && !salidaPedida)) {
  console.error("Uso: npm run sondear -- <zip o carpeta de zips> [--salida archivo.txt]");
  process.exit(2);
}
const zip = resolve(origen);
if (!existsSync(zip)) {
  console.error(`No existe: ${zip}`);
  process.exit(2);
}
const nombre = basename(zip).replace(/\.zip$/i, "");
const salida = resolve(salidaPedida ?? join(API, "infrastructure/build/sonda", `${nombre}.txt`));

// Gradle quiere las rutas con «/» también en Windows, y entre comillas: el .bat corre por el cmd,
// que partiría «Imperio Wicho.zip» en dos argumentos.
const enWindows = process.platform === "win32";
const comoGradle = (ruta) => {
  const conBarras = ruta.replaceAll("\\", "/");
  return enWindows ? `"${conBarras}"` : conBarras;
};
// Con ruta completa: el cmd de Windows no busca el .bat en el directorio de trabajo del hijo.
const gradlew = join(API, enWindows ? "gradlew.bat" : "gradlew");
const resultado = spawnSync(
  gradlew,
  [
    ":infrastructure:sondearExportacion",
    `-Pzip=${comoGradle(zip)}`,
    `-Psalida=${comoGradle(salida)}`,
    "-q",
  ],
  { cwd: API, stdio: "inherit", shell: enWindows },
);
if (resultado.status !== 0) {
  process.exit(resultado.status ?? 1);
}
console.log(`\nSonda de ${basename(zip)}: ${salida}`);
