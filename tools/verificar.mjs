#!/usr/bin/env node
// Orquesta lint + pruebas + build de todo el monorepo. Ver docs/09-plan-de-arranque.md.
//
// Sin argumentos hace todo, que es como se corre en local. Con `--solo-web` o `--solo-api` hace
// una mitad: eso es lo que usa integración continua para correr las dos en paralelo **sin dejar
// de correr este mismo archivo**. Si CI ejecutara su propia lista de comandos, el día que aquí
// se agregue un paso, CI no se enteraría — y una verificación que no verifica lo mismo que la
// local no sirve de guardián.
import { execSync } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));

const soloWeb = process.argv.includes("--solo-web");
const soloApi = process.argv.includes("--solo-api");

function ejecutar(comando, opciones = {}) {
  console.log(`\n> ${comando}`);
  execSync(comando, { stdio: "inherit", shell: true, ...opciones });
}

if (!soloApi) {
  // Primero las capas: es lo más barato y lo más estructural. Si una dependencia se invirtió,
  // da igual que el lint y las pruebas pasen: el diseño ya se rompió, y enterarse en 2 segundos
  // es mejor que enterarse después del build.
  ejecutar("node tools/verificar-capas.mjs");
  // Mismo criterio y mismo precio (poco más de un segundo): un par de color por debajo del
  // mínimo de la WCAG es un defecto, y hasta ahora solo se veía si alguien se acordaba de correr
  // `npm run contrastes` a mano.
  ejecutar("node tools/verificar-contrastes.mjs");
  // Y por el mismo motivo, un marcador [[ ]] en un texto que se publica. Los documentos legales
  // llevaron seis a producción durante una fase entera: la plantilla pinta la clave tal como está
  // y ninguna prueba mira el contenido del texto.
  ejecutar("node tools/verificar-marcadores.mjs");
  // Y por el mismo motivo, un dato del negocio que diga cosas distintas segun donde se lea. El
  // celular del negocio estuvo mal en el pie y en tres parrafos de los legales durante una fase
  // entera, y al corregirlo no quedo nada que impidiera que volviera a pasar.
  ejecutar("node tools/verificar-datos-de-negocio.mjs");
  ejecutar("npm run lint --workspaces --if-present");
  ejecutar("npm test --workspaces --if-present");
  ejecutar("npm run build --workspaces --if-present");
}

if (!soloWeb) {
  // Ruta absoluta al envoltorio, y con el nombre que le toca a esta plataforma. Las dos cosas
  // hacen falta: el proyecto se desarrolla en Windows pero los ejecutores de integración
  // continua son Linux, y hasta hoy este archivo llevaba clavado el `.bat`; y el prefijo
  // relativo tampoco es fiable, porque Git Bash define NoDefaultCurrentDirectoryInExePath y
  // con eso cmd deja de buscar en el directorio actual. Resolviendo desde la ubicación del
  // script, da igual desde dónde se invoque y con qué intérprete.
  const nombre = process.platform === "win32" ? "gradlew.bat" : "gradlew";
  const envoltorio = join(RAIZ, "apps/api", nombre);
  if (existsSync(envoltorio)) {
    ejecutar(`"${envoltorio}" build`, { cwd: join(RAIZ, "apps/api") });
  } else {
    console.log("apps/api todavía no tiene esqueleto de Gradle: se omite.");
  }
}
