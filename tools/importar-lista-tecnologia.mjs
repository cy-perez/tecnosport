#!/usr/bin/env node
// La lista del proveedor de tecnología, de las skills al catálogo, por la API del panel (ADR-0075).
//
// **Simula por omisión.** Sin `--escribir` no toca nada: dice qué mandaría. Importar mueve
// existencias de verdad y subir fotos escribe en un bucket real.
//
// La lista que escribió `exportar_lista.py` entra en dos tiempos (ADR-0079), y hay que decir cuál:
//
//   1. El inventario, el día de la lista, al cerrar la skill de listas (bloque 1):
//
//        node tools/importar-lista-tecnologia.mjs catalogo/5-api/lista-api.json --inventario \
//            --proveedor <id> --escribir
//
//      Lo que ya se vende se renueva —costo y existencia— y lo desaparecido deja de ofrecerse. No
//      propone nada: deja en `por-proponer.json`, junto a la lista, los modelos que no se venden
//      —los que necesitan fotos— y las configuraciones nuevas de los que sí. Y deja, también junto
//      a la lista, una copia exacta de lo que entró (`entro-<fecha>-<huella>.json`): si alguien
//      vuelve a exportar encima, los borradores todavía tienen de dónde salir.
//
//   2. Los borradores, desde la skill de fichas (bloque 3), cuando la tanda de fotos terminó:
//
//        node tools/importar-lista-tecnologia.mjs catalogo/5-api/lista-api.json --borradores \
//            --proveedor <id> --escribir
//
//      Solo lleva lo de `por-proponer.json` que puede revisarse: los modelos nuevos cuyo
//      `estado-fotos.json` dice `apto`, y las configuraciones nuevas de los que ya se venden. Entra
//      o se actualiza el borrador de cada uno y, a los modelos nuevos, les sube sus fotos LISTA en
//      orden —la primera es la principal—. Aprobar en el panel publica el producto con ellas.
//
//      No toca costo ni existencia, así que puede correrse más de una vez: un borrador que ya
//      tiene sus fotos no se vuelve a subir, y uno que quedó a medias se rehace.
//
// Opciones:
//   --inventario    el paso 1
//   --borradores    el paso 2
//   --proveedor ID  el proveedor de tecnología, como lo muestra su ficha en el panel
//   --escribir      hace los cambios de verdad
//   --api URL       por omisión http://localhost:8080
//   --token JWT     o la variable de entorno TS_TOKEN_ADMIN. No pregunta la clave: los prompts
//                   no funcionan desde `!` en Claude Code, y el token no se imprime nunca.

import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import { pathToFileURL } from "node:url";
import { carpetaDeModelo, fotosDeCarpeta } from "./material-catalogo.mjs";
import { paraLaWeb, subirVariantes } from "./subida-de-imagenes.mjs";

const argv = process.argv.slice(2);
const bandera = (nombre) => argv.includes(nombre);
const valor = (nombre, omision = null) => {
  const i = argv.indexOf(nombre);
  if (i < 0) return omision;
  const siguiente = argv[i + 1];
  // Un valor que empieza por `--` es la bandera de al lado: lo mismo que en cargar-catalogo.mjs.
  if (siguiente === undefined || siguiente.startsWith("--")) {
    console.error(`La opción ${nombre} necesita un valor.`);
    process.exit(1);
  }
  return siguiente;
};

const API = valor("--api", "http://localhost:8080").replace(/\/$/, "");
const TOKEN = valor("--token", process.env.TS_TOKEN_ADMIN);
const ESCRIBIR = bandera("--escribir");
const PROVEEDOR = valor("--proveedor");
const INVENTARIO = bandera("--inventario");
const BORRADORES = bandera("--borradores");
/** El mismo nombre que escribe `fotos-estudio-degradado/scripts/procesar_fichas.py`. */
const ESTADO_FOTOS = "estado-fotos.json";
/**
 * El mismo tope que `BorradorTecnologia.TOPE_DE_FOTOS` (1 + `Producto.TOPE_DE_GALERIA`): la
 * principal y la galería entera. Estuvo en 9 hasta el 22/10/2026, una menos que la API.
 */
export const TOPE_DE_FOTOS = 10;

async function pedir(ruta, opciones = {}) {
  const respuesta = await fetch(`${API}${ruta}`, {
    ...opciones,
    headers: {
      "Content-Type": "application/json",
      ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
      ...(opciones.headers ?? {}),
    },
  });
  if (!respuesta.ok) {
    const cuerpo = await respuesta.text();
    throw new Error(`${opciones.method ?? "GET"} ${ruta} respondió ${respuesta.status}: ${cuerpo}`);
  }
  return respuesta.status === 204 ? null : respuesta.json();
}

/** Lo que trae la lista, contado, para leerlo antes de mandarlo. */
export function resumenDeLista(lista) {
  const configuraciones = lista.modelos.reduce((n, m) => n + m.configuraciones.length, 0);
  return (
    `Lista del ${lista.fechaLista} (${(lista.bloques ?? []).join(", ") || "sin bloques"}): ` +
    `${lista.modelos.length} modelos y ${configuraciones} configuraciones; ` +
    `${(lista.configuracionesDesaparecidas ?? []).length} configuraciones y ` +
    `${(lista.modelosDesaparecidos ?? []).length} modelos desaparecidos`
  );
}

/**
 * Lo que se manda en cada tiempo. Los borradores no retiran nada —la API lo rechaza, porque quien
 * los manda creería que lo desaparecido dejó de ofrecerse—, así que la misma lista sirve para los
 * dos: lo desaparecido se queda fuera aquí.
 */
export function cuerpoParaModo(lista, modo) {
  if (modo === "INVENTARIO") return { ...lista, modo };
  if (modo === "BORRADORES") {
    return { ...lista, modo, configuracionesDesaparecidas: [], modelosDesaparecidos: [] };
  }
  throw new Error(`Modo desconocido: ${modo}`);
}

/** El SHA-256 del archivo de la lista, para saber si es el mismo que entró como inventario. */
export function huellaDeArchivo(contenido) {
  return createHash("sha256").update(contenido).digest("hex");
}

/**
 * La entrega del inventario a las skills de fotos y de fichas: lo que espera borrador, la huella
 * del archivo de la lista con que entró y la API que lo respondió. Los borradores se niegan si el
 * archivo cambió después —le propondrían a la API un contenido que el inventario nunca vio— y si
 * van contra otra API: lo que espera borrador lo dijo la base de ese ambiente, no la de este (27
 * de octubre de 2026), como el registro de `cargar-catalogo.mjs`.
 */
export function porProponerDe(lista, resultado, huellaLista = null, api = null) {
  const enLaLista = new Set((lista.modelos ?? []).map((m) => m.idModelo));
  const modelos = resultado.modelosPorProponer ?? [];
  return {
    fechaLista: lista.fechaLista,
    huellaLista,
    api,
    modelos,
    // Lo que la API espera proponer pero esta lista no trae: el bloque 1 no lo exportó —casi
    // siempre por falta de descripción— y el bloque 3 no puede hacerle borrador. La tanda de
    // fotos lo procesa sin bloquearse por él: el Note 17 Pro Max retuvo la tanda entera del
    // 15/10/2026 esperando la cuarta foto de un borrador que no podía existir.
    sinFicha: modelos.filter((id) => !enLaLista.has(id)),
    configuraciones: resultado.configuracionesPorProponer ?? [],
  };
}

/**
 * Por qué los borradores se niegan con un por-proponer.json de otra API, o nulo si es de esta. Uno
 * de antes del 27/10/2026 no dice de cuál es, y se acepta: el ambiente lo decide quien lo corre.
 */
export function mensajeDeOtraApi(porProponer, api) {
  if (!porProponer.api || porProponer.api === api) {
    return null;
  }
  return (
    `por-proponer.json salió del inventario contra ${porProponer.api}, y esto va contra ${api}: ` +
    "lo que espera borrador lo dijo la base de aquel ambiente. Corre --borradores con " +
    `--api ${porProponer.api}, o el inventario de esta lista contra ${api}.`
  );
}

/**
 * El nombre de la copia de lo que entró como inventario. Lleva la huella porque es lo que la
 * identifica: dos exportaciones del mismo día son dos archivos distintos.
 */
export function nombreDeLaQueEntro(fechaLista, huella) {
  return `entro-${fechaLista}-${huella.slice(0, 12)}.json`;
}

/**
 * Guarda la copia exacta de lo que entró como inventario y la verifica releyéndola. La lista del
 * 15/10/2026 quedó con huella en por-proponer.json y sin copia en ninguna parte: los borradores se
 * negaban, con razón, y no había de dónde recuperar lo que el inventario vio. Si la copia no queda
 * idéntica byte a byte, mejor fallar aquí que descubrirlo en el bloque 3.
 */
export function guardarLaQueEntro(carpeta, fechaLista, contenido) {
  const huella = huellaDeArchivo(contenido);
  const copia = join(carpeta, nombreDeLaQueEntro(fechaLista, huella));
  writeFileSync(copia, contenido);
  if (huellaDeArchivo(readFileSync(copia, "utf8")) !== huella) {
    throw new Error(
      `La copia de lo que entró quedó distinta del original (${copia}): sin ella, si la lista se ` +
        "vuelve a exportar, los borradores no tienen de dónde salir. Revisa el disco antes de seguir.",
    );
  }
  return { copia, huella };
}

/**
 * Por qué los borradores se niegan cuando el archivo no es el que entró. Si la copia de lo que
 * entró existe, se dice cuál: con ella los borradores salen del contenido que vio el inventario.
 * Sin copia —un inventario de antes del 21/10/2026— no hay de dónde recuperarlo.
 */
export function mensajeDeListaCambiada(ruta, copia) {
  const base =
    `${ruta} cambió después de que su inventario entró: los borradores llevarían un contenido ` +
    "que el inventario nunca vio.";
  if (copia) {
    return `${base} Lo que entró está en ${copia}: corre --borradores con ese archivo.`;
  }
  return (
    `${base} Y la copia de lo que entró no está. Dos salidas: si productos.json no cambió, ` +
    "vuelve a exportar desde la misma corrida, que da el mismo archivo; si cambió, exporta con " +
    "--reemplazar-la-que-entro y corre otra vez el inventario con el archivo nuevo — repone " +
    "existencias, así que decídelo con la persona antes."
  );
}

/**
 * Lo que va en los borradores: de lo que espera borrador, solo lo que se puede revisar.
 *
 * <p>Un modelo nuevo entra si la tanda de fotos lo dejó `apto` **y trae la paleta del fabricante**;
 * uno en espera o fuera de la tanda, o sin paleta, se queda, con el motivo. Sin paleta la ficha no
 * dice en qué colores existe el modelo, ningún color se puede elegir más que los que marcó la
 * lista, y todo pasa por WhatsApp (ADR-0086): eso se arregla en el bloque 1 con
 * `scripts/paletas.py`, no en el panel. Las configuraciones nuevas de un modelo que ya se vende entran sin
 * mirar fotos —el producto tiene las suyas—, y solo esas: el resto del modelo ya se renovó con el
 * inventario.
 *
 * @param estados {idModelo: contenido de su estado-fotos.json, o null si no tiene}
 */
export function seleccionParaBorradores(lista, porProponer, estados) {
  const nuevos = new Set(porProponer.modelos ?? []);
  const configuraciones = new Set(porProponer.configuraciones ?? []);
  const modelos = [];
  const omitidos = [];
  // Se deriva de la lista y no del campo `sinFicha`, para que un por-proponer.json de antes de
  // que existiera diga lo mismo.
  const enLaLista = new Set(lista.modelos.map((m) => m.idModelo));
  for (const id of nuevos) {
    if (!enLaLista.has(id)) {
      omitidos.push({
        titulo: id,
        motivo:
          "sin ficha en la lista: el bloque 1 no lo exportó —casi siempre por falta de " +
          "descripción—; complétalo ahí, que sin descripción no hay borrador",
      });
    }
  }
  for (const modelo of lista.modelos) {
    if (nuevos.has(modelo.idModelo)) {
      const estado = estados[modelo.idModelo];
      if (estado?.estado === "apto" && (modelo.paleta ?? []).length > 0) {
        modelos.push(modelo);
      } else if (estado?.estado === "apto") {
        omitidos.push({
          titulo: modelo.titulo,
          motivo:
            "sin paleta del fabricante: fíjala en el bloque 1 con `python scripts/paletas.py " +
            `fijar ${modelo.idModelo} --colores … --fuente …\` y vuelve a exportar; un modelo de un ` +
            "solo acabado lleva ese color",
        });
      } else {
        omitidos.push({
          titulo: modelo.titulo,
          motivo: !estado
            ? "no pasó por la tanda de fotos"
            : estado.estado === "fuera_de_tanda"
              ? `fuera de la tanda: ${estado.motivo}`
              : `espera fotos: ${estado.motivo ?? `le faltan ${estado.faltan}`}`,
        });
      }
      continue;
    }
    const nuevas = modelo.configuraciones.filter((c) => configuraciones.has(c.sku));
    if (nuevas.length) modelos.push({ ...modelo, configuraciones: nuevas });
  }
  return { lista: { ...lista, modelos }, omitidos };
}

/**
 * Las tomas que se suben: las LISTA que anotó la tanda, en su orden —la primera es la principal—.
 * Una que la tanda contó y ya no está en la carpeta es un error que se dice, no una foto que se
 * salta: el modelo quedaría con menos de las que pasaron la compuerta.
 */
export function tomasListas(archivos, listas) {
  const porNombre = new Map(archivos.map((a) => [basename(a.ruta), a]));
  // Más de las que caben no se suben: el servidor cortaría en el tope, y cada corrida vería un
  // borrador distinto del esperado y lo rehiciera sin terminar nunca.
  return listas.slice(0, TOPE_DE_FOTOS).map((nombre) => {
    const toma = porNombre.get(nombre);
    if (!toma) {
      throw new Error(`La tanda contó ${nombre} como LISTA y ya no está en «Fotos procesadas».`);
    }
    return toma;
  });
}

/**
 * Si el borrador ya tiene exactamente esas fotos, en ese orden. Por hash y no por cantidad: una
 * tanda que se repitió deja otras cuatro LISTA, y contar cuatro y cuatro publicaría las viejas.
 */
export function mismasFotos(esperadas, actuales) {
  return esperadas.length === actuales.length && esperadas.every((hash, i) => hash === actuales[i]);
}

/** El hash con que se sube una toma: el de su variante mayor, como en `subirVariantes`. */
function hashDeToma(toma, titulo) {
  const web = paraLaWeb(toma, titulo);
  const mayor = web.variantes.find((v) => v.ancho === web.ancho) ?? web.variantes[0];
  return createHash("sha256").update(readFileSync(mayor.ruta)).digest("hex");
}

/** Lo que respondió la importación, en el orden en que alguien lo tiene que leer. */
export function informeDeImportacion(r) {
  const lineas = [
    `${r.productosRenovados} productos renovados, ${r.variantesRepuestas} variantes repuestas`,
    `${r.borradoresNuevos} borradores nuevos y ${r.borradoresActualizados} actualizados: revísalos en /admin/tecnologia`,
    `${r.variantesRetiradas} variantes retiradas, ${r.modelosAgotados} modelos agotados`,
  ];
  if (r.modelosPorProponer?.length || r.configuracionesPorProponer?.length) {
    lineas.push(
      `Esperan borrador: ${r.modelosPorProponer?.length ?? 0} modelos que no se venden —necesitan ` +
        `fotos— y ${r.configuracionesPorProponer?.length ?? 0} configuraciones nuevas de los que sí`,
    );
  }
  if (r.modelosYaDecididos.length) {
    lineas.push(`No se volvieron a proponer (ya rechazados): ${r.modelosYaDecididos.join(", ")}`);
  }
  if (r.sinMargen.length) {
    lineas.push(
      "OJO, quedaron agotadas: el costo nuevo les deja menos del 10 % sobre el contado, y la " +
        "lista no mueve el precio. Súbelo en el panel, que las avisa, o vuelven cuando baje:",
    );
    lineas.push(...r.sinMargen.map((s) => `  - ${s}`));
  }
  if (r.coloresSinVariante?.length) {
    lineas.push(
      "La lista trae colores que el producto no tiene; se añaden desde su ficha en el panel:",
    );
    lineas.push(...r.coloresSinVariante.map((s) => `  - ${s}`));
  }
  return lineas.join("\n");
}

function estadoDeFotos(idModelo) {
  const carpeta = carpetaDeModelo(idModelo);
  const ruta = carpeta ? join(carpeta, ESTADO_FOTOS) : null;
  return ruta && existsSync(ruta) ? JSON.parse(readFileSync(ruta, "utf8")) : null;
}

async function importar(ruta) {
  if (!PROVEEDOR) {
    throw new Error("Falta --proveedor: el id del proveedor de tecnología.");
  }
  if (INVENTARIO === BORRADORES) {
    throw new Error("Di cuál de los dos tiempos: --inventario o --borradores (ADR-0079).");
  }
  const contenido = readFileSync(ruta, "utf8");
  const lista = JSON.parse(contenido);
  if (INVENTARIO) {
    return importarInventario(ruta, lista, contenido);
  }
  return importarBorradores(ruta, lista, contenido);
}

async function importarInventario(ruta, lista, contenido) {
  const cuerpo = cuerpoParaModo(lista, "INVENTARIO");
  console.log(`inventario · ${resumenDeLista(cuerpo)}`);
  if (!ESCRIBIR) {
    console.log("Simulado: añade --escribir para importarla.");
    return;
  }
  const resultado = await pedir(`/api/v1/admin/proveedores/${PROVEEDOR}/listas-tecnologia`, {
    method: "POST",
    body: JSON.stringify(cuerpo),
  });
  console.log(informeDeImportacion(resultado));
  // Antes que por-proponer.json: si la copia no se pudo escribir, que no quede una entrega que
  // apunte a un contenido que nadie guardó.
  const { copia, huella } = guardarLaQueEntro(dirname(ruta), lista.fechaLista, contenido);
  console.log(`Lo que entró quedó copiado en ${copia}`);
  const destino = join(dirname(ruta), "por-proponer.json");
  const entrega = porProponerDe(cuerpo, resultado, huella, API);
  writeFileSync(destino, JSON.stringify(entrega, null, 2) + "\n");
  console.log(`Lo que espera borrador quedó en ${destino}`);
  if (entrega.sinFicha.length) {
    console.log(
      `OJO: ${entrega.sinFicha.length} modelo(s) esperan borrador y no vienen en la lista —el ` +
        "bloque 1 no los exportó, casi siempre por falta de descripción—. Sus fotos se procesan " +
        "pero no saldrá borrador de ellos: " +
        entrega.sinFicha.join(", "),
    );
  }
}

async function importarBorradores(ruta, lista, contenido) {
  const rutaPorProponer = join(dirname(ruta), "por-proponer.json");
  if (!existsSync(rutaPorProponer)) {
    throw new Error(
      `No está ${rutaPorProponer}: los borradores van después del inventario (--inventario).`,
    );
  }
  const porProponer = JSON.parse(readFileSync(rutaPorProponer, "utf8"));
  const otraApi = mensajeDeOtraApi(porProponer, API);
  if (otraApi) {
    throw new Error(otraApi);
  }
  if (porProponer.fechaLista !== lista.fechaLista) {
    throw new Error(
      `por-proponer.json es de la lista del ${porProponer.fechaLista} y esta es del ` +
        `${lista.fechaLista}: corre primero el inventario de esta lista.`,
    );
  }
  if (porProponer.huellaLista && porProponer.huellaLista !== huellaDeArchivo(contenido)) {
    const copia = join(
      dirname(ruta),
      nombreDeLaQueEntro(porProponer.fechaLista, porProponer.huellaLista),
    );
    throw new Error(mensajeDeListaCambiada(ruta, existsSync(copia) ? copia : null));
  }
  const estados = Object.fromEntries(
    (porProponer.modelos ?? []).map((id) => [id, estadoDeFotos(id)]),
  );
  const { lista: seleccion, omitidos } = seleccionParaBorradores(lista, porProponer, estados);
  const cuerpo = cuerpoParaModo(seleccion, "BORRADORES");
  console.log(`borradores · ${resumenDeLista(cuerpo)}`);
  for (const o of omitidos) console.log(`  se queda   ${o.titulo}: ${o.motivo}`);

  const conFotos = seleccion.modelos
    .filter((m) => estados[m.idModelo]?.estado === "apto")
    .map((m) => ({
      modelo: m,
      tomas: tomasListas(
        fotosDeCarpeta(carpetaDeModelo(m.idModelo)).archivos,
        estados[m.idModelo].listas,
      ),
    }));
  for (const { modelo, tomas } of conFotos) {
    console.log(`  fotos      ${modelo.titulo}: ${tomas.length} LISTA`);
  }
  if (!ESCRIBIR) {
    console.log("Simulado: añade --escribir para importarla y subir las fotos.");
    return;
  }
  if (!TOKEN) {
    throw new Error("Hace falta la sesión del panel: --token o TS_TOKEN_ADMIN.");
  }
  if (!cuerpo.modelos.length) {
    console.log("No hay nada que proponer.");
    return;
  }

  const resultado = await pedir(`/api/v1/admin/proveedores/${PROVEEDOR}/listas-tecnologia`, {
    method: "POST",
    body: JSON.stringify(cuerpo),
  });
  console.log(informeDeImportacion(resultado));

  const enRevision = await pedir("/api/v1/admin/borradores-tecnologia?estado=EN_REVISION");
  for (const { modelo, tomas } of conFotos) {
    const borrador = enRevision.find(
      (b) => b.idModelo === modelo.idModelo && b.proveedorId === PROVEEDOR && !b.productoId,
    );
    if (!borrador) {
      console.log(`  sin borrador ${modelo.titulo}: la API no lo propuso (¿ya rechazado?)`);
      continue;
    }
    await subirFotosAlBorrador(borrador, modelo.titulo, tomas);
  }
}

/**
 * Las fotos del modelo al borrador, en orden. Si ya tiene exactamente esas —mismos hashes, mismo
 * orden—, no hace nada; si tiene otras —una corrida que se cayó a mitad, o una tanda que se
 * repitió—, las quita y las sube de nuevo, para que la principal siga siendo la primera.
 */
async function subirFotosAlBorrador(borrador, titulo, tomas) {
  const base = `/api/v1/admin/borradores-tecnologia/${borrador.id}/fotos`;
  const esperadas = tomas.map((toma) => hashDeToma(toma, titulo));
  if (borrador.cantidadDeFotos > 0) {
    const detalle = await pedir(`/api/v1/admin/borradores-tecnologia/${borrador.id}`);
    if (
      mismasFotos(
        esperadas,
        detalle.fotos.map((f) => f.hash),
      )
    ) {
      console.log(`  ya tiene   ${titulo}: sus ${tomas.length} fotos`);
      return;
    }
    for (const foto of detalle.fotos) {
      await pedir(`${base}/${foto.id}`, { method: "DELETE" });
    }
  }
  for (const toma of tomas) {
    await pedir(base, {
      method: "POST",
      body: JSON.stringify(
        await subirVariantes(pedir, `${base}/url-subida`, paraLaWeb(toma, titulo), titulo),
      ),
    });
  }
  console.log(`  subidas    ${titulo}: ${tomas.length} fotos al borrador`);
}

/** Las opciones que llevan valor: lo que va detrás de ellas no es el archivo de la lista. */
const CON_VALOR = new Set(["--proveedor", "--api", "--token"]);

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const archivo = argv.find((a, i) => !a.startsWith("--") && !CON_VALOR.has(argv[i - 1]));
  const tarea = archivo
    ? importar(archivo)
    : Promise.reject(
        new Error(
          "Uso: importar-lista-tecnologia.mjs <lista-api.json> --inventario|--borradores " +
            "--proveedor <id> [--escribir]",
        ),
      );
  tarea.catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
