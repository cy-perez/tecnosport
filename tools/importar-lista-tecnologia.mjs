#!/usr/bin/env node
// La lista del proveedor de tecnología, de la skill al catálogo, por la API del panel (ADR-0075).
//
// **Simula por omisión.** Sin `--escribir` no toca nada: dice qué mandaría. Importar mueve
// existencias de verdad y subir fotos escribe en un bucket real.
//
// Dos pasos, en este orden, con la revisión del panel en medio:
//
//   1. Importar la lista que escribió `exportar_lista.py`:
//
//        node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --proveedor <id> --escribir
//
//      Lo que ya se vende se renueva —costo y existencia—, lo desaparecido deja de ofrecerse, y
//      lo nuevo entra como borrador de tecnología para revisar en /admin/tecnologia.
//
//   2. Después de aprobar en el panel, subir las fotos de los modelos aprobados desde su carpeta
//      de fichas (`catalogo/entregables/fichas/<Modelo>/Fotos procesadas`) y publicarlos:
//
//        node tools/importar-lista-tecnologia.mjs --fotos --escribir --publicar
//
//      Solo toca los productos que no tienen imagen principal: correrlo dos veces no sube nada dos
//      veces. Un modelo sin fotos procesadas se informa y se queda en borrador.
//
// Opciones:
//   --proveedor ID  el proveedor de tecnología, como lo muestra su ficha en el panel
//   --fotos         el paso 2
//   --publicar      con --fotos: publica lo que quedó con imagen principal
//   --escribir      hace los cambios de verdad
//   --api URL       por omisión http://localhost:8080
//   --token JWT     o la variable de entorno TS_TOKEN_ADMIN. No pregunta la clave: los prompts
//                   no funcionan desde `!` en Claude Code, y el token no se imprime nunca.

import { readFileSync } from "node:fs";
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
const PUBLICAR = bandera("--publicar");
const FOTOS = bandera("--fotos");
const PROVEEDOR = valor("--proveedor");
/** El mismo tope que `Producto.TOPE_DE_GALERIA`: la principal y ocho más. */
const TOPE_DE_GALERIA = 8;

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

/** Lo que respondió la importación, en el orden en que alguien lo tiene que leer. */
export function informeDeImportacion(r) {
  const lineas = [
    `${r.productosRenovados} productos renovados, ${r.variantesRepuestas} variantes repuestas`,
    `${r.borradoresNuevos} borradores nuevos y ${r.borradoresActualizados} actualizados: revísalos en /admin/tecnologia`,
    `${r.variantesRetiradas} variantes retiradas, ${r.modelosAgotados} modelos agotados`,
  ];
  if (r.modelosYaDecididos.length) {
    lineas.push(`No se volvieron a proponer (ya rechazados): ${r.modelosYaDecididos.join(", ")}`);
  }
  if (r.sinMargen.length) {
    lineas.push("OJO, el costo alcanzó el precio de venta y la lista no lo mueve:");
    lineas.push(...r.sinMargen.map((s) => `  - ${s}`));
  }
  if (r.coloresSinVariante?.length) {
    lineas.push("La lista trae colores que el producto no tiene; se añaden desde su ficha en el panel:");
    lineas.push(...r.coloresSinVariante.map((s) => `  - ${s}`));
  }
  return lineas.join("\n");
}

async function importar(ruta) {
  if (!PROVEEDOR) {
    throw new Error("Falta --proveedor: el id del proveedor de tecnología.");
  }
  const lista = JSON.parse(readFileSync(ruta, "utf8"));
  console.log(resumenDeLista(lista));
  if (!ESCRIBIR) {
    console.log("Simulado: añade --escribir para importarla.");
    return;
  }
  const resultado = await pedir(`/api/v1/admin/proveedores/${PROVEEDOR}/listas-tecnologia`, {
    method: "POST",
    body: JSON.stringify(lista),
  });
  console.log(informeDeImportacion(resultado));
}

/**
 * Los modelos aprobados que siguen en borrador: sin imagen principal, o con ella pero sin publicar
 * —una corrida anterior sin `--publicar`, o una que se cayó a mitad de la galería—.
 */
async function pendientesDeFotos() {
  const aprobados = await pedir("/api/v1/admin/borradores-tecnologia?estado=APROBADO");
  const porProducto = new Map();
  for (const b of aprobados) {
    if (b.productoId && !porProducto.has(b.productoId)) porProducto.set(b.productoId, b);
  }
  const pendientes = [];
  for (const [productoId, borrador] of porProducto) {
    const producto = await pedir(`/api/v1/admin/productos/${productoId}`);
    if (!producto.imagenPrincipal || producto.estado === "BORRADOR") {
      pendientes.push({ producto, borrador });
    }
  }
  return pendientes;
}

async function subirFotos() {
  if (!TOKEN) {
    throw new Error("Hace falta la sesión del panel: --token o TS_TOKEN_ADMIN.");
  }
  const pendientes = await pendientesDeFotos();
  if (!pendientes.length) {
    console.log("Ningún modelo aprobado espera fotos.");
    return;
  }
  for (const { producto, borrador } of pendientes) {
    if (producto.imagenPrincipal) {
      // Ya tiene fotos: solo falta publicarlo. La galería no se completa aquí; si quedó a medias,
      // se termina desde el panel.
      console.log(
        `${ESCRIBIR && PUBLICAR ? "publicando" : "simulado"}   ${producto.nombre}: ya tiene fotos` +
          (PUBLICAR ? " · se publica" : " · queda en borrador (falta --publicar)"),
      );
      if (ESCRIBIR && PUBLICAR) {
        await pedir(`/api/v1/admin/productos/${producto.id}/publicacion`, { method: "POST" });
      }
      continue;
    }
    const { archivos } = fotosDeCarpeta(carpetaDeModelo(borrador.idModelo));
    if (!archivos.length) {
      console.log(`sin fotos    ${producto.nombre}: no hay "Fotos procesadas" en su carpeta de fichas`);
      continue;
    }
    const [principal, ...resto] = archivos;
    const galeria = resto.slice(0, TOPE_DE_GALERIA);
    console.log(
      `${ESCRIBIR ? "subiendo" : "simulado"}     ${producto.nombre}: principal y ${galeria.length} en galería` +
        (PUBLICAR ? " · se publica" : " · queda en borrador"),
    );
    if (!ESCRIBIR) continue;

    const id = producto.id;
    await pedir(`/api/v1/admin/productos/${id}/imagen-principal`, {
      method: "POST",
      body: JSON.stringify(
        await subirVariantes(
          pedir,
          `/api/v1/admin/productos/${id}/imagen-principal/url-subida`,
          paraLaWeb(principal, producto.nombre),
          producto.nombre,
        ),
      ),
    });
    for (const foto of galeria) {
      await pedir(`/api/v1/admin/productos/${id}/galeria`, {
        method: "POST",
        body: JSON.stringify(
          await subirVariantes(
            pedir,
            `/api/v1/admin/productos/${id}/galeria/url-subida`,
            paraLaWeb(foto, producto.nombre),
            producto.nombre,
          ),
        ),
      });
    }
    if (PUBLICAR) {
      await pedir(`/api/v1/admin/productos/${id}/publicacion`, { method: "POST" });
    }
  }
}

/** Las opciones que llevan valor: lo que va detrás de ellas no es el archivo de la lista. */
const CON_VALOR = new Set(["--proveedor", "--api", "--token"]);

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const archivo = argv.find((a, i) => !a.startsWith("--") && !CON_VALOR.has(argv[i - 1]));
  const tarea = FOTOS ? subirFotos() : archivo ? importar(archivo) : Promise.reject(
    new Error("Uso: importar-lista-tecnologia.mjs <lista-api.json> --proveedor <id> [--escribir]\n" +
      "     importar-lista-tecnologia.mjs --fotos [--publicar] [--escribir]"),
  );
  tarea.catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
