#!/usr/bin/env node
// Regenera packages/contratos/src/tipos.ts desde el OpenAPI del backend.
// Requiere el backend corriendo en local: docker compose up -d && gradlew.bat bootRun.
import { execSync } from "node:child_process";

execSync("npm run generar --workspace=packages/contratos", { stdio: "inherit" });
