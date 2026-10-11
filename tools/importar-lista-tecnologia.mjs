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
//        node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --inventario \
//            --proveedor <id> --escribir
//
//      Lo que ya se vende se renueva —costo y existencia— y lo desaparecido deja de ofrecerse. No
//      propone nada: deja en `por-proponer.json`, junto a la lista, los modelos que no se venden
//      —los que necesitan fotos— y las configuraciones nuevas de los que sí.
//
//   2. Los borradores, desde la skill de fichas (bloque 3), cuando la tanda de fotos terminó:
//
//        node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --borradores \
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

/** La entrega del inventario a las skills de fotos y de fichas: lo que espera borrador. */
export function porProponerDe(lista, resultado) {
  return {
    fechaLista: lista.fechaLista,
    modelos: resultado.modelosPorProponer ?? [],
    configuraciones: resultado.configuracionesPorProponer ?? [],
  };
}

/**
 * Lo que va en los borradores: de lo que espera borrador, solo lo que se puede revisar.
 *
 * <p>Un modelo nuevo entra si la tanda de fotos lo dejó `apto`; uno en espera o fuera de la tanda
 * se queda, con el motivo. Las configuraciones nuevas de un modelo que ya se vende entran sin
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
  for (const modelo of lista.modelos) {
    if (nuevos.has(modelo.idModelo)) {
      const estado = estados[modelo.idModelo];
      if (estado?.estado === "apto") {
        modelos.push(modelo);
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
  return listas.map((nombre) => {
    const toma = porNombre.get(nombre);
    if (!toma) {
      throw new Error(`La tanda contó ${nombre} como LISTA y ya no está en «Fotos procesadas».`);
    }
    return toma;
  });
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
    lineas.push("OJO, el costo alcanzó el precio de venta y la lista no lo mueve:");
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
  const lista = JSON.parse(readFileSync(ruta, "utf8"));
  if (INVENTARIO) {
    return importarInventario(ruta, lista);
  }
  return importarBorradores(ruta, lista);
}

async function importarInventario(ruta, lista) {
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
  const destino = join(dirname(ruta), "por-proponer.json");
  writeFileSync(destino, JSON.stringify(porProponerDe(cuerpo, resultado), null, 2) + "\n");
  console.log(`Lo que espera borrador quedó en ${destino}`);
}

async function importarBorradores(ruta, lista) {
  const rutaPorProponer = join(dirname(ruta), "por-proponer.json");
  if (!existsSync(rutaPorProponer)) {
    throw new Error(
      `No está ${rutaPorProponer}: los borradores van después del inventario (--inventario).`,
    );
  }
  const porProponer = JSON.parse(readFileSync(rutaPorProponer, "utf8"));
  if (porProponer.fechaLista !== lista.fechaLista) {
    throw new Error(
      `por-proponer.json es de la lista del ${porProponer.fechaLista} y esta es del ` +
        `${lista.fechaLista}: corre primero el inventario de esta lista.`,
    );
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
 * Las fotos del modelo al borrador, en orden. Si ya tiene exactamente esas, no hace nada; si tiene
 * otras —una corrida que se cayó a mitad—, las quita y las sube de nuevo, para que la principal
 * siga siendo la primera.
 */
async function subirFotosAlBorrador(borrador, titulo, tomas) {
  if (borrador.cantidadDeFotos === tomas.length) {
    console.log(`  ya tiene   ${titulo}: ${tomas.length} fotos`);
    return;
  }
  const base = `/api/v1/admin/borradores-tecnologia/${borrador.id}/fotos`;
  if (borrador.cantidadDeFotos > 0) {
    const detalle = await pedir(`/api/v1/admin/borradores-tecnologia/${borrador.id}`);
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
