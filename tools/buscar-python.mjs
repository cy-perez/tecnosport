// El intérprete de Python que exista, para los guardianes que corren código Python.
//
// Lo comparten `verificar-kit.mjs` y `verificar-listas.mjs`. Vive aparte porque la pregunta tiene
// una trampa que conviene resolver una sola vez: en Windows `python3` es el alias de la tienda,
// que no es Python, y responde con un cartel y un código de salida distinto de cero.
import { spawnSync } from "node:child_process";

/** `python3` o `python`, el primero que de verdad ejecuta código; `null` si no hay ninguno. */
export function buscarPython() {
  for (const candidato of ["python3", "python"]) {
    const prueba = spawnSync(candidato, ["-c", "print(40 + 2)"], { encoding: "utf8" });
    if (prueba.status === 0 && prueba.stdout.trim() === "42") return candidato;
  }
  return null;
}
