#!/usr/bin/env node
// ¿El kit de marca se regenera igual que como está guardado?
//
// El 21 de septiembre de 2026 no: el comando que documentaba el propio `LEEME.md` del kit dejaba
// el repositorio **peor** que antes de ejecutarlo —perdía los logos, declaraba TTF como woff2 y
// recomendaba la bandera destructiva justo cuando lo era— y nada lo miraba, porque el kit no
// tiene ninguna prueba. Las dos comprobaciones que lo destaparon se hicieron a mano; esto es lo
// que las repite solo.
//
// **Regenera en un temporal, nunca sobre el repositorio.** Un guardián que escribe donde vigila
// no puede distinguir entre "esto ya estaba bien" y "lo acabo de arreglar sin darme cuenta".
//
// Las dos propiedades, y por qué son esas:
//
//   A. Regenerar reproduce lo commiteado, byte a byte. Es la propiedad que le faltaba a este
//      generador y la única que de verdad lo vigila: los tres defectos de aquel día cambiaban
//      alguno de los archivos generados, así que los tres habrían salido aquí.
//
//   B. Si no se puede comprimir a woff2, el generador se niega a rehacer las tipografías. Se
//      comprueba llamando a `por_que_empeoraria` y no corriendo `--fuentes`, y la diferencia
//      importa: con fontTools instalado, `--fuentes` se va a descargar las familias de Google
//      Fonts. Una verificación que necesita red falla los días que falla la red, y eso enseña a
//      ignorarla.
//
// Uso:  node tools/verificar-kit.mjs
import { cpSync, existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const RAIZ = fileURLToPath(new URL("..", import.meta.url));
const KIT = join(RAIZ, "packages/marca");

/** Lo que hay que llevarse al temporal para que regenerar signifique lo mismo que aquí.
 *
 * `tokens.json` es la entrada; `generador/` se copia a sí mismo dentro del kit; y `fuentes.css`,
 * `fuentes/` y `logo/` son lo que el generador **conserva** de la carpeta destino. Sin ellos
 * regeneraría en un kit vacío, que es otro escenario y no el que se quiere vigilar. */
const ENTRADAS = ["tokens.json", "generador", "fuentes.css", "fuentes", "logo"];

/** El intérprete que exista. En Windows `python3` es el alias de la tienda, que no es Python:
 * responde con un cartel y un código de salida distinto de cero, así que se pregunta antes. */
function buscarPython() {
  for (const candidato of ["python3", "python"]) {
    const prueba = spawnSync(candidato, ["-c", "print(40 + 2)"], { encoding: "utf8" });
    if (prueba.status === 0 && prueba.stdout.trim() === "42") return candidato;
  }
  return null;
}

/**
 * Iguales **como los compara git**, que es lo que decide si hay o no un cambio que confirmar.
 *
 * `.gitattributes` declara `* text=auto eol=lf`: el repositorio guarda LF y normaliza al
 * confirmar. Python en Windows escribe los archivos de texto en CRLF, así que una comparación
 * byte a byte marca los seis archivos generados como distintos en la máquina de quien programa y
 * como idénticos en integración continua. Un guardián que solo dispara en un sistema operativo
 * no dice nada del kit: dice en qué máquina se corrió.
 *
 * Lo binario sí se compara byte a byte —ahí un `\r` es contenido, no formato—, y qué es binario
 * se decide con la misma heurística que usa git: si tiene un byte cero, no es texto.
 */
function iguales(recien, guardado) {
  const binario = recien.includes(0) || guardado.includes(0);
  if (binario) return recien.equals(guardado);
  const enLineas = (contenido) => contenido.toString("utf8").replaceAll("\r\n", "\n");
  return enLineas(recien) === enLineas(guardado);
}

/** Todos los archivos de un directorio, en rutas relativas a él. */
function archivosDe(directorio, base = directorio) {
  return readdirSync(directorio, { withFileTypes: true }).flatMap((entrada) => {
    const ruta = join(directorio, entrada.name);
    return entrada.isDirectory()
      ? archivosDe(ruta, base)
      : [relative(base, ruta).replaceAll("\\", "/")];
  });
}

const python = buscarPython();
if (!python) {
  // En integración continua falla: un guardián que se salta solo deja de ser un guardián. En
  // local avisa, porque quien está tocando el frontend no tiene por qué tener Python puesto.
  const mensaje =
    "No hay Python. El kit se genera con `generador/kit_ui.py`, así que sin intérprete no se" +
    " puede comprobar que siga regenerándose igual.";
  if (process.env.CI) {
    console.error(`ERROR: ${mensaje}`);
    process.exit(1);
  }
  console.log(`AVISO: ${mensaje}\n       Se omite la verificación del kit.`);
  process.exit(0);
}

const temporal = mkdtempSync(join(tmpdir(), "kit-"));
try {
  for (const entrada of ENTRADAS) {
    const origen = join(KIT, entrada);
    if (!existsSync(origen)) {
      console.error(`ERROR: al kit le falta '${entrada}', que es de donde se regenera.`);
      process.exit(1);
    }
    cpSync(origen, join(temporal, entrada), { recursive: true });
  }

  // El comando exacto que documenta el LEEME del kit, corrido desde dentro del kit. Es el que
  // usa quien lo recibe, y era el que hacía daño: se vigila ese, no una variante nuestra.
  const generado = spawnSync(python, ["generador/kit_ui.py", "tokens.json", "--out", "."], {
    cwd: temporal,
    encoding: "utf8",
  });
  if (generado.status !== 0) {
    console.error("ERROR: el generador del kit falló.\n");
    console.error(generado.stdout || "");
    console.error(generado.stderr || "");
    process.exit(1);
  }

  const diferencias = [];
  const sobran = [];
  for (const archivo of archivosDe(temporal)) {
    const enElRepositorio = join(KIT, archivo);
    if (!existsSync(enElRepositorio)) {
      sobran.push(archivo);
      continue;
    }
    const recien = readFileSync(join(temporal, archivo));
    const guardado = readFileSync(enElRepositorio);
    if (!iguales(recien, guardado)) {
      diferencias.push({
        archivo,
        bytes: `${guardado.length} guardados → ${recien.length} regenerados`,
      });
    }
  }

  if (diferencias.length > 0 || sobran.length > 0) {
    console.error("El kit no se regenera igual que como está guardado.\n");
    for (const { archivo, bytes } of diferencias) {
      console.error(`  cambia   packages/marca/${archivo}  (${bytes})`);
    }
    for (const archivo of sobran) {
      console.error(`  aparece  packages/marca/${archivo}  (el generador lo produce y no está)`);
    }
    console.error(
      "\nUna de dos: alguien editó a mano un archivo generado, o el generador dejó de producir" +
        "\nlo que produjo. Las dos se arreglan en el mismo sitio —`tokens.json` y `generador/`—," +
        "\nnunca editando el archivo que sale.",
    );
    process.exit(1);
  }

  // Propiedad B, sin red: la guarda que impide rehacer las tipografías cuando no se pueden
  // comprimir. Vive en `fuentes.py` y la usan los dos caminos, `fuentes.py` y `kit_ui --fuentes`.
  const guarda = spawnSync(
    python,
    [
      "-c",
      [
        "import sys; sys.path.insert(0, 'generador')",
        "import fuentes",
        "motivo = fuentes.por_que_empeoraria('fuentes', False)",
        "print('SE NIEGA' if motivo else 'ACEPTA')",
      ].join("; "),
    ],
    { cwd: temporal, encoding: "utf8" },
  );
  if (guarda.status !== 0 || guarda.stdout.trim() !== "SE NIEGA") {
    console.error(
      "El generador aceptaría rehacer las tipografías sin poder comprimirlas a woff2.\n" +
        "Eso deja .ttf declarados como woff2: el navegador los carga adivinando por los bytes," +
        "\nasí que nada se rompe a la vista y nadie se entera.",
    );
    console.error(guarda.stdout || "", guarda.stderr || "");
    process.exit(1);
  }

  const cuantos = archivosDe(temporal).length;
  console.log(
    `El kit se regenera igual que como está guardado (${cuantos} archivos, con ${python}),` +
      " y se niega a rehacer las tipografías sin con qué comprimirlas.",
  );
} finally {
  rmSync(temporal, { recursive: true, force: true });
}
