#!/usr/bin/env node
// Orquesta lint + pruebas + build de todo el monorepo. Ver docs/09-plan-de-arranque.md.
import { execSync } from "node:child_process";
import { existsSync } from "node:fs";

function ejecutar(comando, opciones = {}) {
  console.log(`\n> ${comando}`);
  execSync(comando, { stdio: "inherit", shell: true, ...opciones });
}

ejecutar("npm run lint --workspaces --if-present");
ejecutar("npm test --workspaces --if-present");
ejecutar("npm run build --workspaces --if-present");

if (existsSync("apps/api/gradlew.bat")) {
  ejecutar(".\\gradlew.bat build", { cwd: "apps/api" });
} else {
  console.log("\napps/api todavía no tiene esqueleto de Gradle: se omite.");
}
