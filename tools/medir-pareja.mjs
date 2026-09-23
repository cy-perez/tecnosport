#!/usr/bin/env node
// Mide dos versiones del frontend, alternando el orden, y dice si la diferencia se repite.
//
// Existe por lo que costó la deuda 22. Un cambio de rendimiento se "comprobó" comparando dos
// corridas de sesiones distintas y salió una mejora de 145 ms que no existía: medida como toca,
// eran 40. `medir-lighthouse.mjs` ya impide esa comparación —cada corrida lleva su etiqueta y la
// tabla nunca dice "sí" para un tiempo—, pero montar el experimento seguía siendo a mano: sacar
// las plantillas de un commit, construir, medir, sacarlas del otro, construir, medir, y acordarse
// de dejar el árbol como estaba.
//
// Lo que aporta esto sobre hacerlo a mano es el **orden alternado** y el **veredicto por repetición**:
//
//   pareja 1:  antes → despues
//   pareja 2:  despues → antes        <- invertida a propósito
//
// Si la máquina se va calentando durante los veinte minutos que dura esto, medir siempre en el
// mismo orden le regala la mejora al segundo. Con el orden alternado, un arrastre monótono empuja
// a las dos parejas en sentidos contrarios y se ve. Lo que sobrevive a eso es lo que se puede
// contar: una diferencia que aparece con el mismo signo en las dos parejas.
//
// Uso:
//   node tools/medir-pareja.mjs --antes <commit> --despues <commit> --prefijo defer
//   node tools/medir-pareja.mjs --solo-resumen defer     (vuelve a sacar el veredicto, sin medir)
//   node tools/medir-pareja.mjs --antes X --despues Y --simular   (dice qué haría y no hace nada)
//
// Requiere lo mismo que el arnés: PostgreSQL y la API arriba.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const INFORMES = join(RAIZ, "apps/web/lighthouse");
const PUERTO_API = 8080;

/** Las métricas de tiempo que tiene sentido enfrentar, y los bytes aparte.
 *
 * La diferencia no es de importancia: los bytes no dependen del reloj, así que ahí una diferencia
 * es la diferencia y no hace falta repetir nada. Los tiempos son los que necesitan el experimento
 * entero. */
const TIEMPOS = ["rendimiento", "fcp", "lcp", "tbt", "eval. scripts", "estilo y layout"];
const BYTES = ["peso kB", "fuentes kB"];

function argumento(nombre, porOmision = null) {
  const posicion = process.argv.indexOf(nombre);
  if (posicion === -1) return porOmision;
  const valor = process.argv[posicion + 1];
  if (!valor || valor.startsWith("--")) {
    throw new Error(`${nombre} necesita un valor.`);
  }
  return valor;
}

function lista(nombre) {
  const posicion = process.argv.indexOf(nombre);
  if (posicion === -1) return null;
  const valores = [];
  for (let i = posicion + 1; i < process.argv.length && !process.argv[i].startsWith("--"); i++) {
    valores.push(process.argv[i]);
  }
  return valores.length ? valores : null;
}

function git(...argumentos) {
  const resultado = spawnSync("git", argumentos, { cwd: RAIZ, encoding: "utf8" });
  if (resultado.status !== 0) {
    throw new Error(`git ${argumentos.join(" ")} falló:\n${resultado.stderr || resultado.stdout}`);
  }
  return resultado.stdout.trim();
}

/**
 * Este script escribe sobre el árbol de trabajo —saca archivos de un commit y de otro—, así que
 * un cambio sin confirmar se perdería. Se niega antes de tocar nada en vez de pedir perdón
 * después: lo que hay en el árbol puede ser media hora de trabajo de alguien.
 */
function exigirArbolLimpio(archivos) {
  const sucio = git("status", "--porcelain", "--", ...archivos);
  if (sucio) {
    throw new Error(
      "Hay cambios sin confirmar en los archivos que este experimento va a intercambiar:\n" +
        sucio
          .split("\n")
          .map((linea) => `  ${linea}`)
          .join("\n") +
        "\n\nConfirma o guarda esos cambios antes: el experimento los sobrescribe.",
    );
  }
}

/**
 * Lo que entra al build del frontend, que es lo unico que puede cambiar una medicion.
 *
 * <p>Empezo siendo solo `apps/web/src`, y eso dejaba fuera dos carpetas que si entran, las dos
 * descubiertas el 23 de septiembre de 2026:
 *
 * <ul>
 *   <li><b>`packages/marca`</b>, porque `prebuild` corre `copiar-marca.mjs` y mete el kit dentro
 *       de `apps/web/src/assets/marca` y de `apps/web/public` en <b>cada</b> build. Un cambio del
 *       kit —el `font-display` de `fuentes.css`, ese dia— se intercambiaba en la copia y
 *       `prebuild` la volvia a pisar con la del arbol: las dos mitades del experimento salian del
 *       mismo build, y nada lo decia.
 *   <li><b>`apps/web/public`</b>, que no esta bajo `src` y pesa lo que pesa: ahi vive el hero de
 *       la portada. El experimento del hero, el 22 de septiembre, no se habria podido montar con
 *       esta herramienta.
 * </ul>
 *
 * <p>Lo que sigue fuera lo sigue estando a proposito: un cambio en `docs/` o en `tools/` no entra
 * en el build, y moverlo entre corrida y corrida solo anade ruido.
 */
const RUTAS_DEL_BUILD = ["apps/web/src", "apps/web/public", "packages/marca"];

function archivosQueCambian(antes, despues) {
  const salida = git("diff", "--name-only", antes, despues, "--", ...RUTAS_DEL_BUILD);
  return salida ? salida.split("\n").filter(Boolean) : [];
}

/**
 * El caso que el alcance nuevo no arregla solo: una copia del kit que cambio <b>sin</b> su
 * original.
 *
 * <p>`apps/web/src/assets/marca/` es copia entera de `packages/marca`, y `prebuild` la reescribe
 * antes de construir. Si el diff la toca y no toca el kit, intercambiarla no sirve de nada: las
 * dos corridas saldrian del mismo build. Ocurre cuando alguien edita a mano un archivo generado,
 * que es justo lo que prohibe la regla 3 del `CLAUDE.md`, asi que el experimento se niega y dice
 * donde esta el original.
 */
function exigirQueLasCopiasTraiganSuOriginal(archivos) {
  const copias = archivos.filter((a) => a.startsWith("apps/web/src/assets/marca/"));
  const hayKit = archivos.some((a) => a.startsWith("packages/marca/"));
  if (copias.length && !hayKit) {
    throw new Error(
      "Estos archivos son copias que `prebuild` reescribe desde packages/marca en cada build:\n" +
        copias.map((c) => `  ${c}`).join("\n") +
        "\n\nIntercambiarlos no cambia el build: las dos corridas medirian lo mismo. El" +
        " original\nesta en packages/marca — cambialo ahi y regenera (ver docs/04-ui-marca.md).",
    );
  }
}

function medianaDe(valores) {
  const limpios = valores.filter((v) => typeof v === "number").sort((a, b) => a - b);
  if (limpios.length === 0) return null;
  return limpios[Math.floor((limpios.length - 1) / 2)];
}

function leerResumen(etiqueta) {
  const ruta = join(INFORMES, etiqueta, "resumen.json");
  if (!existsSync(ruta)) throw new Error(`Falta la corrida '${etiqueta}' (${ruta}).`);
  return JSON.parse(readFileSync(ruta, "utf8"));
}

function medirLado(etiqueta) {
  const resultado = spawnSync("npm", ["run", "lighthouse", "--", "--etiqueta", etiqueta], {
    cwd: RAIZ,
    stdio: "inherit",
    shell: true,
  });
  if (resultado.status !== 0) {
    throw new Error(`La corrida '${etiqueta}' falló. El arnés ya dijo por qué, arriba.`);
  }
}

/**
 * El veredicto, que es lo único que este script sabe y el arnés no: si la diferencia **se repite**.
 *
 * <p>Una pareja sola no decide un tiempo —eso ya lo dice `--comparar`, y por eso su respuesta más
 * afirmativa es "quizá"—. Aquí hay varias, y lo que se mira es el signo: una diferencia que
 * aparece en el mismo sentido en todas las parejas es creíble aunque cada una por separado caiga
 * dentro del ruido.
 *
 * <p>Se dice además la probabilidad de que ese acuerdo sea casualidad, que con dos parejas es una
 * de cada cuatro. No es mucho, y decirlo es justo el punto: nadie debería citar esto como una
 * demostración.
 */
function veredictoDeSigno(deltas) {
  // Una pareja que no se movió no vota. Contarla como acuerdo fue el primer defecto que tuvo
  // esto, y se vio con datos de verdad: `legales` daba "+2" y "=" en rendimiento, y el veredicto
  // decía "2/2 en el mismo sentido". Uno de los dos no iba en ningún sentido.
  const movidas = deltas.filter((d) => d !== 0);
  const ceros = deltas.length - movidas.length;
  if (movidas.length === 0) return "sin cambio";
  if (movidas.some((d) => d > 0) && movidas.some((d) => d < 0)) {
    return "NO se repite: no hubo cambio medible";
  }
  if (movidas.length === 1) {
    return `una sola pareja se movio (${ceros} sin cambio): no decide`;
  }
  const n = movidas.length;
  return `consistente: ${n}/${n} en el mismo sentido${ceros ? ` (${ceros} sin cambio)` : ""}`;
}

function resumir(prefijo, parejas) {
  const etiquetas = [];
  for (let i = 1; i <= parejas; i++) {
    etiquetas.push([`${prefijo}-antes-${i}`, `${prefijo}-despues-${i}`]);
  }

  const resumenes = etiquetas.map(([a, b]) => [leerResumen(a), leerResumen(b)]);
  const pantallas = resumenes[0][0].pantallas.map((p) => p.pantalla);

  console.log(`\nVeredicto de '${prefijo}', sobre ${parejas} parejas en orden alternado.\n`);

  for (const pantalla of pantallas) {
    const filas = [];
    for (const metrica of [...TIEMPOS, ...BYTES]) {
      const deltas = resumenes.map(([a, b]) => {
        const pa = a.pantallas.find((p) => p.pantalla === pantalla);
        const pb = b.pantallas.find((p) => p.pantalla === pantalla);
        const ma = medianaDe(pa?.muestras.map((m) => m[metrica]) ?? []);
        const mb = medianaDe(pb?.muestras.map((m) => m[metrica]) ?? []);
        return ma === null || mb === null ? null : mb - ma;
      });
      if (deltas.some((d) => d === null)) continue;

      const fila = { metrica };
      deltas.forEach((delta, i) => {
        fila[`pareja ${i + 1}`] = delta === 0 ? "=" : `${delta > 0 ? "+" : ""}${delta}`;
      });
      // Los bytes no necesitan repetición, pero sí coherencia: si las dos parejas no dan lo mismo
      // es que los builds no eran los que se creía, y eso hay que verlo.
      fila.veredicto = BYTES.includes(metrica)
        ? new Set(deltas).size === 1
          ? deltas[0] === 0
            ? "sin cambio"
            : "sí: son bytes"
          : "ojo: los bytes no coinciden entre parejas"
        : veredictoDeSigno(deltas);
      filas.push(fila);
    }
    console.log(pantalla);
    console.table(filas);
  }

  const casualidadCiega = 2 ** (parejas - 1);
  console.log(
    "\nUn 'consistente' no es una demostracion: es que el signo se repitio. Cada pareja por\n" +
      "separado sigue cayendo dentro del ruido —eso lo dice 'npm run lighthouse -- --comparar'—,\n" +
      "y lo unico que se afirma sin reservas son los bytes.\n" +
      `\nCon ${parejas} parejas, que todas caigan del mismo lado por azar tiene probabilidad 1 de ` +
      `${casualidadCiega}; si\nademas el sentido era el que se esperaba ANTES de medir, 1 de ` +
      `${2 ** parejas}. Cual de las dos\naplica lo sabe quien hizo el cambio, no este script: ` +
      "por eso la tabla dice el signo y no\nun veredicto estadistico.",
  );
}

async function main() {
  const soloResumen = argumento("--solo-resumen");
  const parejas = Number(argumento("--parejas", "2"));
  if (!Number.isInteger(parejas) || parejas < 1) {
    throw new Error(`--parejas pide un entero de 1 en adelante, no '${argumento("--parejas")}'.`);
  }

  if (soloResumen) {
    resumir(soloResumen, parejas);
    return;
  }

  const antes = argumento("--antes");
  const despues = argumento("--despues");
  const prefijo = argumento("--prefijo");
  const simular = process.argv.includes("--simular");
  if (!antes || !despues || !prefijo) {
    throw new Error(
      "Uso:  node tools/medir-pareja.mjs --antes <commit> --despues <commit> --prefijo <nombre>\n" +
        "      node tools/medir-pareja.mjs --solo-resumen <nombre>",
    );
  }
  if (!/^[a-z0-9][a-z0-9-]*$/i.test(prefijo)) {
    throw new Error(`--prefijo pide un nombre simple (letras, digitos y guiones), no '${prefijo}'.`);
  }

  const shaAntes = git("rev-parse", "--short", antes);
  const shaDespues = git("rev-parse", "--short", despues);
  const archivos = lista("--archivos") ?? archivosQueCambian(antes, despues);
  if (archivos.length === 0) {
    throw new Error(
      `Entre ${antes} y ${despues} no cambia ningun archivo de ${RUTAS_DEL_BUILD.join(", ")}.\n` +
        "No hay nada que medir: los dos builds saldrian identicos.",
    );
  }

  console.log(`Experimento '${prefijo}': ${shaAntes} contra ${shaDespues}.`);
  console.log(`Se intercambian ${archivos.length} archivo(s):`);
  for (const archivo of archivos) console.log(`  ${archivo}`);
  console.log(
    `\n${parejas} pareja(s), en orden alternado, ${parejas * 2} corridas de Lighthouse.\n` +
      "Cada una construye el frontend entero: cuenta unos cinco minutos por corrida.\n",
  );

  // Antes de `--simular` a proposito: negarse tambien es parte de "que haria".
  exigirQueLasCopiasTraiganSuOriginal(archivos);

  if (simular) {
    for (let i = 1; i <= parejas; i++) {
      const orden = i % 2 === 1 ? ["antes", "despues"] : ["despues", "antes"];
      console.log(`  pareja ${i}: ${orden.map((l) => `${prefijo}-${l}-${i}`).join("  ->  ")}`);
    }
    console.log("\n--simular: no se construyo ni se midio nada.");
    return;
  }

  exigirArbolLimpio(archivos);

  // Se pregunta aquí y no se deja para el arnés: el arnés lo comprueba también, pero después de
  // construir el frontend entero. Enterarse de que la API no está a los cinco minutos de empezar
  // un experimento de veinte duele bastante más.
  const responde = await fetch(`http://localhost:${PUERTO_API}/api/v1/salud`)
    .then((r) => r.ok)
    .catch(() => false);
  if (!responde) {
    throw new Error(
      `La API no responde en :${PUERTO_API}, y este experimento va a tardar veinte minutos.\n` +
        "Levanta 'docker compose up -d' y 'gradlew.bat bootRun' antes de empezar.",
    );
  }

  // Devolver el árbol a donde estaba pase lo que pase. Sin esto, cortar el experimento a la mitad
  // —que es lo normal cuando se ve venir un resultado malo— deja las plantillas de un commit
  // ajeno en el árbol, y eso se descubre tres commits después.
  const restaurar = () => {
    try {
      git("checkout", despues, "--", ...archivos);
      console.log(`\nArbol restaurado a ${shaDespues}.`);
    } catch (error) {
      console.error(`\nNo se pudo restaurar el arbol: ${error.message}`);
      console.error(`Hazlo a mano:  git checkout ${despues} -- ${archivos.join(" ")}`);
    }
  };
  process.on("SIGINT", () => {
    restaurar();
    process.exit(130);
  });

  try {
    for (let i = 1; i <= parejas; i++) {
      const orden = i % 2 === 1 ? ["antes", "despues"] : ["despues", "antes"];
      for (const lado of orden) {
        const commit = lado === "antes" ? antes : despues;
        console.log(`\n=== pareja ${i}, ${lado} (${lado === "antes" ? shaAntes : shaDespues}) ===`);
        git("checkout", commit, "--", ...archivos);
        medirLado(`${prefijo}-${lado}-${i}`);
      }
    }
  } finally {
    restaurar();
  }

  resumir(prefijo, parejas);
  console.log(
    "\nEl detalle de una pareja, con las bandas de cada corrida:\n" +
      `  npm run lighthouse -- --comparar ${prefijo}-antes-1 ${prefijo}-despues-1`,
  );
}

try {
  await main();
} catch (error) {
  console.error(`\n${error.message}`);
  process.exit(1);
}
