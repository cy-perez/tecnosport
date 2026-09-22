#!/usr/bin/env node
// Regenera packages/contratos/src/tipos.ts desde packages/contratos/openapi.json.
//
// **Ya no hace falta el backend corriendo.** Hasta el 21 de septiembre de 2026 esto leía
// `http://localhost:8080/api/openapi.json`, así que regenerar el cliente exigía Docker, la base
// y un `bootRun` — y comprobar que estuviera al día exigía lo mismo, por lo que solo se
// comprobaba en integración continua. Ahora el OpenAPI vive guardado en el repositorio y es
// `ContratoOpenApiTest` quien vigila, dentro de `gradlew build`, que esa instantánea sea la que
// la aplicación sirve de verdad.
//
// O sea que mover el contrato son dos pasos y no uno:
//   1. cd apps/api && gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true
//   2. npm run contratos
// Los dos archivos que salen —openapi.json y tipos.ts— se commitean juntos con el cambio del
// backend que los movió.
import { execSync } from "node:child_process";

execSync("npm run generar --workspace=packages/contratos", { stdio: "inherit" });
