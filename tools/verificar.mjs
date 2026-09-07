#!/usr/bin/env node
// Orquesta lint + pruebas + build de todo el monorepo. Ver docs/09-plan-de-arranque.md.
import { execSync } from "node:child_process";
import { existsSync } from "node:fs";

function ejecutar(comando, opciones = {}) {
  console.log(`\n> ${comando}`);
  execSync(comando, { stdio: "inherit", shell: true, ...opciones });
}

// Primero las capas: es lo más barato y lo más estructural. Si una dependencia se invirtió,
// da igual que el lint y las pruebas pasen: el diseño ya se rompió, y enterarse en 2 segundos
// es mejor que enterarse después del build.
ejecutar("node tools/verificar-capas.mjs");
ejecutar("npm run lint --workspaces --if-present");
ejecutar("npm test --workspaces --if-present");
ejecutar("npm run build --workspaces --if-present");

if (existsSync("apps/api/gradlew.bat")) {
  ejecutar(".\\gradlew.bat build", { cwd: "apps/api" });
} else {
  console.log("\napps/api todavía no tiene esqueleto de Gradle: se omite.");
}
