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

/** Lo que `kit_ui.py tokens.json --out .` produce, escrito a mano **a propósito**.
 *
 * La lista de lo que la corrida produce la deriva este guardián solo; la que no puede derivar es
 * la de ayer. Esta es esa, y la discrepancia entre las dos es justo la señal que faltaba: un
 * generado que dejó de producirse y sigue commiteado no cambia de bytes ni falta del
 * repositorio, así que ninguna de las dos comprobaciones que ya había lo veía. Se queda ahí,
 * idéntico y muerto, y quien lo abre lo lee como vigente.
 *
 * Las cinco son incondicionales en `kit_ui.py`. `fuentes.css` no está porque solo lo escribe
 * `--fuentes` —sin la bandera el generador lo conserva, que es por lo que entra como entrada— y
 * `dist/` tampoco: sale de otro camino que este guardián no corre. */
const GENERADOS = ["LEEME.md", "contraste.md", "index.html", "tipografia.md", "tokens.css"];

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

/** Todos los archivos de un directorio, en rutas relativas a él, menos el bytecode de Python.
 *
 * `__pycache__/` lo escribe el intérprete al importar un módulo, no el generador, y en una
 * máquina limpia no viene en la copia porque `.gitignore` lo excluye. Hoy no rompía por un pelo:
 * el `.pyc` aparece al importar `fuentes`, que es el paso siguiente, cuando la comparación ya
 * terminó. Basta mover ese paso —o que `kit_ui.py` importe `fuentes` al arrancar, como ya hace
 * con `--fuentes`— para que el guardián empiece a fallar en integración continua diciendo que el
 * generador produce un `.pyc` que falta del repositorio. Un fallo así no dice nada del kit: dice
 * qué intérprete corrió. */
function archivosDe(directorio, base = directorio) {
  return readdirSync(directorio, { withFileTypes: true }).flatMap((entrada) => {
    if (entrada.isDirectory() && entrada.name === "__pycache__") return [];
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

/**
 * Salir del bloque sin saltarse la limpieza.
 *
 * `process.exit()` no ejecuta los `finally` pendientes, así que las cuatro salidas que había aquí
 * dentro dejaban en %TEMP% una copia del kit en cada corrida fallida — y una corrida fallida es
 * justo la que uno repite diez veces seguidas mientras arregla el generador.
 */
class SalidaDelKit extends Error {
  constructor(codigo) {
    super(`salida ${codigo}`);
    this.codigo = codigo;
  }
}

try {
  for (const entrada of ENTRADAS) {
    const origen = join(KIT, entrada);
    if (!existsSync(origen)) {
      console.error(`ERROR: al kit le falta '${entrada}', que es de donde se regenera.`);
      throw new SalidaDelKit(1);
    }
    cpSync(origen, join(temporal, entrada), { recursive: true });
  }

  // Lo que hay antes de generar es, por construcción, lo que se acaba de copiar: las entradas.
  // Con esa fotografía, lo que el generador produce se sabe restando, y nadie tiene que mantener
  // aparte la lista de qué archivo del kit sale de dónde.
  const antesDeGenerar = new Set(archivosDe(temporal));

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
    throw new SalidaDelKit(1);
  }

  const producidos = archivosDe(temporal).filter((archivo) => !antesDeGenerar.has(archivo));
  // Solo los que además están guardados: si el generador produce algo que el repositorio no
  // tiene, eso ya lo dice `sobran`, y un informe que nombra el mismo archivo dos veces con dos
  // etiquetas distintas se lee como dos problemas.
  const sinDeclarar = producidos.filter(
    (archivo) => !GENERADOS.includes(archivo) && existsSync(join(KIT, archivo)),
  );
  const dejaronDeProducirse = GENERADOS.filter((archivo) => !producidos.includes(archivo));

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

  if (
    diferencias.length > 0 ||
    sobran.length > 0 ||
    sinDeclarar.length > 0 ||
    dejaronDeProducirse.length > 0
  ) {
    console.error("El kit no se regenera igual que como está guardado.\n");
    for (const { archivo, bytes } of diferencias) {
      console.error(`  cambia   packages/marca/${archivo}  (${bytes})`);
    }
    for (const archivo of sobran) {
      console.error(`  aparece  packages/marca/${archivo}  (el generador lo produce y no está)`);
    }
    for (const archivo of sinDeclarar) {
      console.error(
        `  produce  packages/marca/${archivo}  (lo produce el generador y GENERADOS no lo nombra)`,
      );
    }
    for (const archivo of dejaronDeProducirse) {
      console.error(
        existsSync(join(KIT, archivo))
          ? `  huérfano packages/marca/${archivo}  (dejó de producirse y sigue guardado)`
          : `  se fue   packages/marca/${archivo}  (dejó de producirse; tampoco está guardado)`,
      );
    }
    console.error(
      "\nUna de dos: alguien editó a mano un archivo generado, o el generador dejó de producir" +
        "\nlo que produjo. Las dos se arreglan en el mismo sitio —`tokens.json` y `generador/`—," +
        "\nnunca editando el archivo que sale." +
        "\n\nSi la salida cambió a propósito, el cambio no termina en el generador: lo que ya no" +
        "\nse produce hay que borrarlo del repositorio, y lo nuevo hay que declararlo en" +
        "\n`GENERADOS`. Un generado huérfano no falla nunca y se lee como vigente.",
    );
    throw new SalidaDelKit(1);
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
    throw new SalidaDelKit(1);
  }

  const conservados = archivosDe(temporal).length - producidos.length;
  console.log(
    `El kit se regenera igual que como está guardado (${producidos.length} generados sobre` +
      ` ${conservados} conservados, con ${python}), produce exactamente los ${GENERADOS.length}` +
      " declarados, y se niega a rehacer las tipografías sin con qué comprimirlas.",
  );
} catch (error) {
  if (!(error instanceof SalidaDelKit)) throw error;
  process.exitCode = error.codigo;
} finally {
  rmSync(temporal, { recursive: true, force: true });
}
