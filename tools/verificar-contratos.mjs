#!/usr/bin/env node
// ¿`packages/contratos/src/tipos.ts` corresponde al OpenAPI guardado?
//
// Es el segundo de los dos eslabones del contrato. El primero —que el OpenAPI guardado sea el que
// la aplicación sirve de verdad— lo vigila `ContratoOpenApiTest` dentro de `gradlew build`.
//
// **Por qué existe este archivo.** El guardián vivía entero en integración continua: un trabajo
// que levantaba PostgreSQL, arrancaba `bootRun`, esperaba a que respondiera, regeneraba el cliente
// y exigía que el diff quedara vacío. Funcionaba, pero avisaba después del empujón y ya sobre la
// rama; y mientras tanto, en local, `npm run verificar` pasaba en verde con el cliente viejo. Con
// la instantánea del OpenAPI guardada en el repositorio, esta comprobación no necesita ni red ni
// Docker ni que alguien tenga el `bootRun` levantado: tarda lo que tarda regenerar un archivo.
//
// **Se regenera en un temporal, nunca sobre el archivo que se vigila.** Un guardián que escribe
// donde comprueba no distingue "esto estaba bien" de "lo acabo de arreglar sin darme cuenta" — la
// misma razón por la que `verificar-kit.mjs` regenera el kit en otro sitio.
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const INSTANTANEA = join(RAIZ, "packages/contratos/openapi.json");
const CLIENTE = join(RAIZ, "packages/contratos/src/tipos.ts");
// El ejecutable de verdad y no el envoltorio de `node_modules/.bin`: en Windows ese es un `.cmd`
// que obliga a `shell: true`, y con shell una ruta con un espacio o una tilde vuelve a ser un
// problema de comillas. Invocarlo con node es igual en los dos sistemas.
const GENERADOR = join(RAIZ, "node_modules/openapi-typescript/bin/cli.js");

/** Para salir con código propio sin saltarse el `finally` que borra el temporal. */
class SalidaDelContrato extends Error {
  constructor(codigo) {
    super(`salida ${codigo}`);
    this.codigo = codigo;
  }
}

/** Como compara git: el final de línea no es una diferencia del contrato. */
function enLineas(texto) {
  return texto.replace(/\r\n/g, "\n");
}

let temporal;
try {
  for (const [ruta, queEs] of [
    [INSTANTANEA, "la instantánea del OpenAPI"],
    [CLIENTE, "el cliente generado"],
    [GENERADOR, "openapi-typescript"],
  ]) {
    if (!existsSync(ruta)) {
      console.error(`No está ${queEs}: ${ruta}`);
      if (ruta === INSTANTANEA) {
        console.error(
          "\nSe crea corriendo la prueba del contrato con la bandera que la reescribe:\n" +
            '  cd apps/api && gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest"' +
            " -PactualizarContrato=true",
        );
      }
      if (ruta === GENERADOR) {
        console.error("\nFalta instalar las dependencias: npm ci");
      }
      throw new SalidaDelContrato(1);
    }
  }

  temporal = mkdtempSync(join(tmpdir(), "tecnosport-contrato-"));
  const recien = join(temporal, "tipos.ts");

  // El mismo comando que `npm run generar --workspace=packages/contratos`, con otra salida. Se
  // vigila ese, no una variante nuestra: si el proyecto cambia de generador o de banderas, esta
  // comprobación tiene que cambiar con él o deja de comprobar lo que se usa.
  const generado = spawnSync(process.execPath, [GENERADOR, INSTANTANEA, "-o", recien], {
    cwd: join(RAIZ, "packages/contratos"),
    encoding: "utf8",
  });

  if (generado.status !== 0) {
    console.error("No se pudo regenerar el cliente desde la instantánea del OpenAPI.\n");
    console.error(generado.stderr || generado.stdout || "(sin salida)");
    throw new SalidaDelContrato(1);
  }

  if (enLineas(readFileSync(recien, "utf8")) !== enLineas(readFileSync(CLIENTE, "utf8"))) {
    console.error(
      "El cliente generado no corresponde al OpenAPI guardado.\n\n" +
        "  packages/contratos/src/tipos.ts  (regenerarlo da otro archivo)\n\n" +
        "Se arregla corriendo `npm run contratos` y commiteándolo con el cambio que lo movió.\n" +
        "Nunca editando tipos.ts a mano: es generado, y el próximo `npm run contratos` lo pisa.\n\n" +
        "Si lo que cambió es el backend, el contrato se mueve en dos pasos y no en uno:\n" +
        '  1. cd apps/api && gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest"' +
        " -PactualizarContrato=true\n" +
        "  2. npm run contratos",
    );
    throw new SalidaDelContrato(1);
  }

  const rutas = Object.keys(JSON.parse(readFileSync(INSTANTANEA, "utf8")).paths ?? {}).length;
  console.log(
    `El cliente generado corresponde al OpenAPI guardado (${rutas} rutas), y la instantánea` +
      " la vigila ContratoOpenApiTest contra la aplicación de verdad.",
  );
} catch (error) {
  if (!(error instanceof SalidaDelContrato)) throw error;
  process.exitCode = error.codigo;
} finally {
  if (temporal) rmSync(temporal, { recursive: true, force: true });
}
