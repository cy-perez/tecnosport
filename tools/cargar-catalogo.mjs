#!/usr/bin/env node
// Carga productos de la lista del proveedor por la misma API que usa el panel: producto, imagen
// principal a Cloud Storage con URL firmada, variante y —si se pide— publicación.
//
// **Por la API y no por la base**: escribir SQL a mano se salta las invariantes del dominio y mete
// el catálogo por una puerta que nadie más va a volver a usar.
//
// **Simula por omisión.** Sin `--escribir` no toca nada: imprime lo que haría. Esto sube objetos a
// un bucket real y crea filas reales, así que el modo seguro es el que no hay que recordar.
//
//   node tools/cargar-catalogo.mjs --ids jbl-flip-7,jbl-grip
//   node tools/cargar-catalogo.mjs --listos --escribir --publicar
//
// Opciones:
//   --ids a,b,c     los ids de catalogo/productos.json a cargar
//   --listos        todos los que no tienen faltas (ver material-catalogo.mjs)
//   --margen-minimo N  deja fuera los que dejen menos de N% sobre el costo. La lista del
//                   proveedor trae productos cuyo precio de mercado es igual o menor que lo que
//                   cuestan, y publicarlos es vender a pérdida. Es una regla y no una lista
//                   escrita a mano para que la siguiente lista se filtre igual.
//   --existencia N  unidades de la entrada inicial. **Por omisión 0**, y no es un descuido:
//                   inventarse un conteo fue el defecto que adr/0049 y adr/0050 tuvieron que
//                   limpiar. El número sale de contar la bodega, y para eso está el panel.
//   --publicar      publica al terminar. Sin esto quedan en BORRADOR, fuera de la vitrina.
//   --escribir      hace los cambios de verdad
//   --api URL       por omisión http://localhost:8080
//   --token JWT     o la variable de entorno TS_TOKEN_ADMIN
//   --correo X      inicia sesión él mismo: pregunta la clave en la terminal, sin eco. La clave
//                   no pasa por `argv` ni por el historial, y el token no se imprime nunca.
//   --medir SKU=peso,largo,ancho,alto   corrige el paquete de una variante que ya existe, por el
//                   mismo endpoint del panel. Se puede repetir. Gramos y centímetros enteros.
//                   Va aquí y no en un script propio porque necesita exactamente lo mismo que la
//                   carga: la sesión del panel y el catálogo de variantes para resolver el SKU.
//   --galeria SKU   sube a la galería las tomas que faltan de un producto que ya está cargado,
//                   por el SKU de su variante. Se puede repetir, y `--galeria-todos` lo hace con
//                   todo lo que anota el registro. Existe porque las cargas anteriores a esto
//                   solo subían la imagen principal: había cuatro tomas de estudio por producto y
//                   a la ficha llegaba una.
//   --publicar-sku SKU   publica un producto que ya está cargado, por su SKU. Se puede repetir.
//                   El panel **no sabe publicar** —no hay ninguna acción de publicación en el
//                   frontend—, así que hoy esta es la única puerta que no pasa por la base.
//
// El SKU y el slug de cada producto quedan anotados en catalogo/cargados.json, que es lo que
// permite volver a correr esto sin duplicar nada — y lo que faltaba la primera vez, cuando
// `jbl-extreme-4` terminó publicado como `jbl-xtreme-4` sin que nada guardara la equivalencia.

import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { CATALOGO, leerJson, leerMaterial } from "./material-catalogo.mjs";

const argv = process.argv.slice(2);
const bandera = (nombre) => argv.includes(nombre);
const valor = (nombre, omision = null) => {
  const i = argv.indexOf(nombre);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : omision;
};
/** Todos los valores de una opción que se puede repetir. */
const valores = (nombre) =>
  argv.flatMap((arg, i) => (arg === nombre && argv[i + 1] ? [argv[i + 1]] : []));

const API = valor("--api", "http://localhost:8080").replace(/\/$/, "");
const CORREO = valor("--correo");
let TOKEN = valor("--token", process.env.TS_TOKEN_ADMIN);
const ESCRIBIR = bandera("--escribir");
const PUBLICAR = bandera("--publicar");
const EXISTENCIA = Number.parseInt(valor("--existencia", "0"), 10);
const MARGEN_MINIMO = Number.parseFloat(valor("--margen-minimo", "0"));
const REGISTRO = join(CATALOGO, "cargados.json");

/** Lo que queda sobre la venta después del costo, en tanto por ciento. */
const margenDe = (producto) =>
  producto.precio_proveedor_cop && producto.precio_mercado_cop
    ? ((producto.precio_mercado_cop - producto.precio_proveedor_cop) /
        producto.precio_mercado_cop) *
      100
    : null;

/** Las categorías de la lista del proveedor y su slug en el catálogo. */
const CATEGORIAS = {
  celulares: "celulares",
  tablets: "tablets",
  parlantes: "parlantes",
  relojes: "relojes",
  audifonos: "audifonos",
  cargadores: "cargadores",
  consolas: "consolas",
  power_bank: "power-banks",
  proyectores: "proyectores",
  computadores: "computadores",
};

/**
 * El SKU sale del id de la lista, en mayúsculas y recortado a lo que admite la columna. Una regla
 * mecánica y no un nombre a mano: tiene que poder recalcularse igual en la siguiente corrida, que
 * es de lo que depende que volver a correr esto no duplique nada.
 */
const skuDe = (id) => id.toUpperCase().slice(0, 60);

/** El precio de venta es el de mercado, no el del proveedor. Vender al costo no es vender. */
const precioDe = (producto) => producto.precio_mercado_cop;

/**
 * La clave, leída de la terminal sin eco. Sin dependencias: el modo crudo de stdin es lo que
 * usan las que existen, y aquí hacen falta veinte líneas, no un paquete.
 */
function preguntarClave(pregunta) {
  return new Promise((resolve, reject) => {
    if (!process.stdin.isTTY) {
      reject(new Error("No hay terminal donde preguntar la clave. Usa --token."));
      return;
    }
    process.stdout.write(pregunta);
    process.stdin.setRawMode(true);
    process.stdin.resume();
    let clave = "";
    process.stdin.on("data", function escuchar(bloque) {
      for (const byte of bloque) {
        if (byte === 3) {
          // Ctrl-C mientras se escribe una clave tiene que salir, no dejar la terminal en crudo.
          process.stdin.setRawMode(false);
          process.stdout.write("\n");
          process.exit(130);
        }
        if (byte === 13 || byte === 10) {
          process.stdin.setRawMode(false);
          process.stdin.pause();
          process.stdin.off("data", escuchar);
          process.stdout.write("\n");
          resolve(clave);
          return;
        }
        if (byte === 127 || byte === 8) clave = clave.slice(0, -1);
        else clave += String.fromCharCode(byte);
      }
    });
  });
}

/**
 * Inicia sesión y se queda el token en memoria. Mira el estado de la respuesta antes de creerse
 * nada: un login fallido que devuelve un cuerpo sin `accessToken` se convertiría, si no, en un
 * token con la palabra `undefined` dentro y en un 403 más adelante que no dice por qué.
 */
async function iniciarSesion(correo) {
  const clave = await preguntarClave(`Clave de ${correo}: `);
  const respuesta = await fetch(`${API}/api/v1/auth/sesion`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo, clave }),
  });
  if (!respuesta.ok) {
    throw new Error(
      `El inicio de sesión respondió ${respuesta.status}.` +
        (respuesta.status === 401 ? " Correo o clave incorrectos." : ""),
    );
  }
  const sesion = await respuesta.json();
  if (sesion.rol !== "ADMIN") {
    throw new Error(`Esa cuenta tiene rol ${sesion.rol}, y el panel exige ADMIN.`);
  }
  if (!sesion.accessToken) {
    throw new Error("El inicio de sesión no devolvió token.");
  }
  return sesion.accessToken;
}

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

/** El mismo tope que `Producto.TOPE_DE_GALERIA`. El que manda es el servidor; esto evita pedir
 * una URL firmada para un objeto que va a quedar huérfano cuando la confirmación responda 409. */
const TOPE_DE_GALERIA = 8;

/**
 * Sube un archivo a la galería del producto: URL firmada, PUT a Cloud Storage, confirmación.
 * Los mismos tres pasos de la imagen principal, contra el subrecurso `galeria`.
 */
async function subirAGaleria(productoId, foto, titulo) {
  const bytes = readFileSync(foto.ruta);
  const subida = await pedir(`/api/v1/admin/productos/${productoId}/galeria/url-subida`, {
    method: "POST",
    body: JSON.stringify({ contentType: "image/jpeg" }),
  });
  const puesta = await fetch(subida.url, {
    method: "PUT",
    headers: { "Content-Type": "image/jpeg" },
    body: bytes,
  });
  if (!puesta.ok) {
    throw new Error(`La subida de la imagen a Cloud Storage respondió ${puesta.status}`);
  }
  return pedir(`/api/v1/admin/productos/${productoId}/galeria`, {
    method: "POST",
    body: JSON.stringify({
      objectKey: subida.objectKey,
      ancho: foto.ancho,
      alto: foto.alto,
      hash: createHash("sha256").update(bytes).digest("hex"),
      altEs: `${titulo} sobre fondo gris`,
      altEn: `${titulo} on a grey background`,
    }),
  });
}

/**
 * Las tomas que van a la galería: todas menos la primera, que es la principal.
 *
 * <p>El estándar de estudio produce cuatro por producto y hasta ahora llegaba una sola a la ficha.
 */
const fotosDeGaleria = (producto) => producto.foto.archivos.slice(1, TOPE_DE_GALERIA + 1);

function descripcion(producto) {
  const prosa = producto.prosa;
  const vinetas = (prosa.vinetas ?? []).map((v) => `• ${v}`).join("\n");
  return [prosa.apertura, vinetas].filter(Boolean).join("\n\n");
}

async function cargarUno(producto, catalogos, registro) {
  const sku = skuDe(producto.id);
  const categoriaSlug = CATEGORIAS[producto.categoria];
  if (!categoriaSlug) {
    throw new Error(`La categoría '${producto.categoria}' de la lista no tiene equivalente.`);
  }
  // Sin token no hay catálogos que consultar, y una simulación que se cae por eso no sirve para
  // lo único que se le pide: ver qué va a pasar antes de que pase.
  const categoria = catalogos.categorias.find((c) => c.slug === categoriaSlug) ?? {
    nombre: categoriaSlug,
  };
  const marca = catalogos.marcas.find((m) => m.nombre === producto.marca) ?? {
    nombre: producto.marca,
  };
  if (ESCRIBIR && !categoria.id) {
    throw new Error(`La categoría '${categoriaSlug}' no existe en el catálogo.`);
  }
  if (ESCRIBIR && !marca.id) {
    throw new Error(`La marca '${producto.marca}' no existe en el catálogo.`);
  }

  const foto = producto.foto.archivos[0];
  const bytes = readFileSync(foto.ruta);
  const hash = createHash("sha256").update(bytes).digest("hex");
  const paquete = producto.empaque;
  const medidas = paquete
    ? `${paquete.pesoGramos} g, ${paquete.largoCm}x${paquete.anchoCm}x${paquete.altoCm} cm`
    : "sin medir (solo recogida)";
  if (producto.fichaDeOtroProducto) {
    console.log(`            ojo: ${producto.fichaDeOtroProducto}`);
  }

  console.log(
    `${ESCRIBIR ? "cargando" : "simulado"}    ${producto.titulo}\n` +
      `            ${sku} · $${precioDe(producto).toLocaleString("es-CO")} · ${categoria.nombre}` +
      ` · existencia ${EXISTENCIA} · ${medidas}` +
      `${PUBLICAR ? " · se publica" : " · queda en BORRADOR"}`,
  );
  if (!ESCRIBIR) return null;

  const creado = await pedir("/api/v1/admin/productos", {
    method: "POST",
    body: JSON.stringify({
      nombre: producto.titulo,
      descripcion: descripcion(producto),
      marcaId: marca.id,
      categoriaId: categoria.id,
    }),
  });

  const subida = await pedir(`/api/v1/admin/productos/${creado.id}/imagen-principal/url-subida`, {
    method: "POST",
    body: JSON.stringify({ contentType: "image/jpeg" }),
  });
  const puesta = await fetch(subida.url, {
    method: "PUT",
    headers: { "Content-Type": "image/jpeg" },
    body: bytes,
  });
  if (!puesta.ok) {
    throw new Error(`La subida de la imagen a Cloud Storage respondió ${puesta.status}`);
  }

  await pedir(`/api/v1/admin/productos/${creado.id}/imagen-principal`, {
    method: "POST",
    body: JSON.stringify({
      objectKey: subida.objectKey,
      ancho: foto.ancho,
      alto: foto.alto,
      hash,
      altEs: `${producto.titulo} sobre fondo gris`,
      altEn: `${producto.titulo} on a grey background`,
    }),
  });

  // La galería va antes que la variante a propósito: si algo falla subiendo fotos, el producto
  // queda sin variante y por tanto sin SKU, que es justo lo que `yaEstaCargado` mira para no
  // duplicarlo en la siguiente corrida.
  let enGaleria = 0;
  for (const otra of fotosDeGaleria(producto)) {
    await subirAGaleria(creado.id, otra, producto.titulo);
    enGaleria++;
  }

  await pedir("/api/v1/admin/variantes", {
    method: "POST",
    body: JSON.stringify({
      productoId: creado.id,
      sku,
      precio: precioDe(producto),
      tasaIva: "0.00",
      codigoBarras: null,
      existenciaInicial: EXISTENCIA,
      pesoGramos: paquete?.pesoGramos ?? null,
      largoCm: paquete?.largoCm ?? null,
      anchoCm: paquete?.anchoCm ?? null,
      altoCm: paquete?.altoCm ?? null,
      atributos: [],
    }),
  });

  if (PUBLICAR) {
    await pedir(`/api/v1/admin/productos/${creado.id}/publicacion`, { method: "POST" });
  }

  registro[producto.id] = {
    productoId: creado.id,
    slug: creado.slug,
    sku,
    cargadoEn: new Date().toISOString(),
    publicado: PUBLICAR,
    imagenesDeGaleria: enGaleria,
  };
  writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}\n`, "utf8");
  return registro[producto.id];
}

const { productos } = leerMaterial();
const porId = new Map(productos.map((p) => [p.id, p]));
const mediciones = valores("--medir");
const aPublicar = valores("--publicar-sku");
const aRellenarGaleria = bandera("--galeria-todos")
  ? Object.values(leerJson(REGISTRO) ?? {}).map((anotado) => anotado.sku)
  : valores("--galeria");
const pedidos = bandera("--listos")
  ? productos.filter((p) => p.faltas.length === 0).map((p) => p.id)
  : (valor("--ids") ?? "").split(",").filter(Boolean);

if (
  pedidos.length === 0 &&
  mediciones.length === 0 &&
  aPublicar.length === 0 &&
  aRellenarGaleria.length === 0
) {
  console.error(
    "Hay que decir qué hacer: --ids a,b,c, --listos, --medir SKU=peso,largo,ancho,alto," +
      " --publicar-sku SKU, --galeria SKU o --galeria-todos.",
  );
  process.exit(1);
}
if (CORREO && !TOKEN) {
  try {
    TOKEN = await iniciarSesion(CORREO);
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
if (ESCRIBIR && !TOKEN) {
  console.error(
    "Falta el token del panel: --correo <correo> para iniciar sesión aquí mismo, --token <jwt>," +
      " o la variable TS_TOKEN_ADMIN. Sin él no se puede escribir.",
  );
  process.exit(1);
}
if (!Number.isInteger(EXISTENCIA) || EXISTENCIA < 0) {
  console.error("--existencia tiene que ser un entero mayor o igual que cero.");
  process.exit(1);
}

/**
 * Corrige el paquete de variantes que ya existen. Sale temprano: medir no es cargar, y mezclar
 * las dos cosas en una corrida haría difícil saber qué pasó con cuál.
 */
async function medir(peticiones) {
  const existencias = await pedir("/api/v1/admin/variantes/existencias");
  const porSku = new Map(existencias.items.map((v) => [v.sku, v]));
  let corregidas = 0;

  for (const peticion of peticiones) {
    const [sku, cifras] = peticion.split("=");
    const [pesoGramos, largoCm, anchoCm, altoCm] = (cifras ?? "")
      .split(",")
      .map((n) => Number.parseInt(n, 10));
    if ([pesoGramos, largoCm, anchoCm, altoCm].some((n) => !Number.isInteger(n) || n <= 0)) {
      console.error(`FALLÓ       ${sku}: se esperan cuatro enteros mayores que cero, "${cifras}"`);
      process.exitCode = 1;
      continue;
    }
    const variante = porSku.get(sku);
    if (!variante) {
      console.error(`FALLÓ       ${sku}: no hay ninguna variante activa con ese SKU`);
      process.exitCode = 1;
      continue;
    }
    console.log(
      `${ESCRIBIR ? "midiendo" : "simulado"}    ${variante.nombreProducto} (${sku})` +
        ` → ${pesoGramos} g, ${largoCm}x${anchoCm}x${altoCm} cm`,
    );
    if (!ESCRIBIR) continue;
    await pedir(`/api/v1/admin/variantes/${variante.varianteId}/paquete`, {
      method: "PATCH",
      body: JSON.stringify({ pesoGramos, largoCm, anchoCm, altoCm }),
    });
    corregidas++;
  }
  console.log(`\n${ESCRIBIR ? "corregidas" : "se corregirían"}: ${corregidas}`);
}

// El registro se lee antes de cualquier rama: publicar también lo escribe, no solo cargar.
const registro = leerJson(REGISTRO) ?? {};

if (aPublicar.length > 0) {
  if (!TOKEN) {
    console.error(
      "Publicar necesita sesión hasta para simularlo: el SKU se resuelve preguntándole al" +
        " catálogo. Usa --correo <correo> o --token <jwt>.",
    );
    process.exit(1);
  }
  await publicarSkus(aPublicar, registro);
  process.exit(process.exitCode ?? 0);
}

if (aRellenarGaleria.length > 0) {
  if (!TOKEN) {
    console.error(
      "Rellenar la galería necesita sesión hasta para simularlo: el SKU se resuelve" +
        " preguntándole al catálogo. Usa --correo <correo> o --token <jwt>.",
    );
    process.exit(1);
  }
  await rellenarGalerias(aRellenarGaleria, registro);
  process.exit(process.exitCode ?? 0);
}

if (mediciones.length > 0) {
  if (!TOKEN) {
    console.error(
      "Medir necesita sesión hasta para simularlo: el SKU se resuelve preguntándole al catálogo.\n" +
        "Usa --correo <correo> o --token <jwt>.",
    );
    process.exit(1);
  }
  await medir(mediciones);
  process.exit(process.exitCode ?? 0);
}

/**
 * Publica productos que ya están cargados, por el SKU de su variante. Sale temprano, como medir:
 * son operaciones distintas y mezclarlas en una corrida haría difícil saber qué pasó con cuál.
 *
 * <p>El estado anterior se lee antes: publicar algo que ya estaba publicado no falla —el caso de
 * uso es idempotente— pero decir "publicado" de algo que ya lo estaba esconde que el SKU pedido no
 * era el que se creía.
 */
/**
 * Sube a la galería las tomas que faltan de productos ya cargados. Sale temprano como medir y
 * publicar, por lo mismo.
 *
 * <p><b>Un producto que ya tenga galería no se toca</b>, y esa es toda la idempotencia que hace
 * falta. Comparar foto por foto pediría el hash de cada imagen ya subida, que la API no devuelve
 * —y no debería: sirve para una cosa, y esa cosa la decide el servidor rechazando duplicados—.
 * Intentarlo y dejar que responda 409 costaría subir el archivo al bucket para descubrir que
 * sobra, y ese objeto no lo reclama nadie.
 */
async function rellenarGalerias(skus, registro) {
  const existencias = await pedir("/api/v1/admin/variantes/existencias");
  const porSku = new Map(existencias.items.map((v) => [v.sku, v]));
  const idPorSku = new Map(
    Object.entries(registro).map(([id, anotado]) => [anotado.sku, id]),
  );
  let subidas = 0;

  for (const sku of skus) {
    const variante = porSku.get(sku);
    if (!variante) {
      console.error(`FALLÓ       ${sku}: no hay ninguna variante activa con ese SKU`);
      process.exitCode = 1;
      continue;
    }
    const id = idPorSku.get(sku);
    const producto = id ? porId.get(id) : null;
    if (!producto) {
      console.error(
        `FALLÓ       ${sku}: el registro no dice de qué producto de la lista salió, así que no` +
          " hay de dónde sacar sus fotos",
      );
      process.exitCode = 1;
      continue;
    }

    const detalle = await pedir(`/api/v1/admin/productos/${variante.productoId}`);
    if ((detalle.galeria ?? []).length > 0) {
      console.log(
        `saltado     ${sku}: ${variante.nombreProducto} ya tiene` +
          ` ${detalle.galeria.length} imagen(es) de galería`,
      );
      continue;
    }

    const fotos = fotosDeGaleria(producto);
    if (fotos.length === 0) {
      console.log(
        `saltado     ${sku}: ${variante.nombreProducto} no tiene más tomas que la principal`,
      );
      continue;
    }

    console.log(
      `${ESCRIBIR ? "subiendo" : "simulado"}    ${variante.nombreProducto} (${sku})` +
        ` → ${fotos.length} imagen(es) a la galería`,
    );
    if (!ESCRIBIR) continue;

    for (const foto of fotos) {
      await subirAGaleria(variante.productoId, foto, producto.titulo);
      subidas++;
    }
    if (id) {
      registro[id] = { ...registro[id], imagenesDeGaleria: fotos.length };
      writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}
`, "utf8");
    }
  }
  console.log(`
${ESCRIBIR ? "subidas" : "se subirían"}: ${subidas}`);
}

async function publicarSkus(skus, registro) {
  const existencias = await pedir("/api/v1/admin/variantes/existencias");
  const porSku = new Map(existencias.items.map((v) => [v.sku, v]));
  let publicados = 0;

  /**
   * El registro tiene que quedar diciendo la verdad, publique este comando o descubra que ya
   * estaba publicado. Existe para que se sepa qué se cargó y en qué estado quedó sin ir a
   * preguntarle a la base, y un registro que miente es peor que no tenerlo.
   */
  const anotarPublicado = (sku) => {
    const id = Object.keys(registro).find((clave) => registro[clave].sku === sku);
    if (!id || registro[id].publicado) return false;
    registro[id] = { ...registro[id], publicado: true, publicadoEn: new Date().toISOString() };
    writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}\n`, "utf8");
    return true;
  };

  for (const sku of skus) {
    const variante = porSku.get(sku);
    if (!variante) {
      console.error(`FALLÓ       ${sku}: no hay ninguna variante activa con ese SKU`);
      process.exitCode = 1;
      continue;
    }
    if (variante.estadoProducto === "PUBLICADO") {
      const corregido = ESCRIBIR && anotarPublicado(sku);
      console.log(
        `saltado     ${sku}: ${variante.nombreProducto} ya estaba publicado` +
          (corregido ? " (el registro decía que no; corregido)" : ""),
      );
      continue;
    }
    console.log(
      `${ESCRIBIR ? "publicando" : "simulado"}  ${variante.nombreProducto} (${sku})` +
        ` · saldo ${variante.saldoTotal}` +
        `${variante.saldoTotal === 0 ? " — sale a la vitrina marcado AGOTADO" : ""}`,
    );
    if (!ESCRIBIR) continue;
    await pedir(`/api/v1/admin/productos/${variante.productoId}/publicacion`, { method: "POST" });
    anotarPublicado(sku);
    publicados++;
  }
  console.log(`\n${ESCRIBIR ? "publicados" : "se publicarían"}: ${publicados}`);
}

if (!TOKEN) {
  console.log(
    "Sin token: esta simulación no puede preguntarle al catálogo qué hay ya cargado, así que va\n" +
      "a decir que carga cosas que quizá ya existen. Con token, la misma simulación las salta.\n",
  );
}
const catalogos = TOKEN
  ? {
      marcas: (await pedir("/api/v1/admin/marcas")).items,
      categorias: (await pedir("/api/v1/admin/categorias")).items,
    }
  : { marcas: [], categorias: [] };
const skusExistentes = TOKEN
  ? new Set((await pedir("/api/v1/admin/variantes/existencias")).items.map((v) => v.sku))
  : new Set();
const nombresExistentes = TOKEN
  ? new Set(
      (await pedir("/api/v1/admin/productos?tamano=200")).items.map((p) => p.nombre.toLowerCase()),
    )
  : new Set();

/**
 * Tres guardas y no una, porque cada una tapa un agujero distinto: el registro cubre lo que cargó
 * este script, el SKU cubre lo que cargó cualquier otra cosa, y el nombre cubre el caso que de
 * verdad hace daño — `CrearProducto` le pone un sufijo al slug que choca, así que un duplicado no
 * falla: se publica otra vez con otra URL y nadie se entera.
 */
function yaEstaCargado(id, producto) {
  if (registro[id]) return "ya lo cargó este script";
  if (skusExistentes.has(skuDe(id))) return "ya hay una variante con ese SKU";
  if (nombresExistentes.has(producto.titulo.toLowerCase())) return "ya hay un producto con ese nombre";
  return null;
}

let cargados = 0;
let saltados = 0;
for (const id of pedidos) {
  const producto = porId.get(id);
  if (!producto) {
    console.log(`saltado     ${id}: no está en la lista`);
    saltados++;
    continue;
  }
  if (producto.faltas.length > 0) {
    console.log(`saltado     ${id}: ${producto.faltas.join(", ")}`);
    saltados++;
    continue;
  }
  const margen = margenDe(producto);
  if (MARGEN_MINIMO > 0 && (margen === null || margen < MARGEN_MINIMO)) {
    console.log(
      `saltado     ${id}: deja ${margen === null ? "un margen desconocido" : `${Math.round(margen)}%`}` +
        ` sobre el costo, menos del ${MARGEN_MINIMO}% pedido`,
    );
    saltados++;
    continue;
  }
  const motivo = yaEstaCargado(id, producto);
  if (motivo) {
    console.log(`saltado     ${id}: ${motivo}`);
    saltados++;
    continue;
  }
  try {
    await cargarUno(producto, catalogos, registro);
    cargados++;
  } catch (error) {
    console.error(`FALLÓ       ${id}: ${error.message}`);
    process.exitCode = 1;
  }
}

console.log(
  `\n${ESCRIBIR ? "cargados" : "se cargarían"}: ${cargados} · saltados: ${saltados}` +
    (ESCRIBIR ? `\nRegistro en ${REGISTRO}` : "\n\nNada de esto pasó: falta --escribir."),
);
