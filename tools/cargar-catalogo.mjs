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
//   --rehacer-imagenes   vuelve a subir la principal y la galería de todo lo que anota el
//                   registro, con la variante web de hoy. Existe porque las cargas anteriores al
//                   21 de septiembre de 2026 subían la maestra del estudio —635 kB— y eso es el
//                   elemento más pesado de la portada y de la ficha.
//   --reconciliar   anota en el registro lo que el catálogo ya tiene y el registro no sabe.
//                   `cargados.json` nació el 21 de septiembre y los doce primeros productos
//                   reales se cargaron el 19: para el registro no existen, así que
//                   `--galeria-todos` no les puede rellenar la galería y se quedaron en la
//                   vitrina con una sola toma de las cuatro que hay en el estudio.
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
import { CATALOGO, leerJson, leerMaterial, margenDe } from "./material-catalogo.mjs";

const argv = process.argv.slice(2);
const bandera = (nombre) => argv.includes(nombre);
const valor = (nombre, omision = null) => {
  const i = argv.indexOf(nombre);
  if (i < 0) return omision;
  const siguiente = argv[i + 1];
  // Un valor que empieza por `--` es la bandera de al lado, no el valor de esta. Sin esta
  // comprobación, `--margen-minimo --listos` dejaba `parseFloat("--listos")` en NaN y el filtro
  // del margen desaparecía **sin una sola línea de aviso**: medido, 12 productos cargados donde
  // debían ser 8, con los cuatro que se venden al costo entre ellos.
  if (siguiente === undefined || siguiente.startsWith("--")) {
    console.error(`La opción ${nombre} necesita un valor.`);
    process.exit(1);
  }
  return siguiente;
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

// Las ramas salen temprano en orden fijo, así que `--medir X --publicar-sku Y` publicaba y no
// medía, sin una palabra. Salir temprano está bien; ignorar en silencio lo que se pidió, no.
const MODOS = ["--reconciliar", "--publicar-sku", "--galeria", "--medir"].filter((n) =>
  argv.includes(n),
);
if (MODOS.length > 1) {
  console.error(
    `Esas opciones no se combinan: ${MODOS.join(", ")}. Cada una es una corrida aparte, y` +
      " mezclarlas haría difícil saber qué pasó con cuál. Córrelas una por una.",
  );
  process.exit(1);
}



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
    // Los bytes se juntan y se decodifican al final, no uno a uno. Una "ñ" en UTF-8 llega como
    // dos bytes (0xC3 0xB1) y `String.fromCharCode` por byte los convertía en dos caracteres
    // distintos: la clave que viajaba no era la que se tecleó, el login respondía 401 y el script
    // lo explicaba como "correo o clave incorrectos" — el diagnóstico equivocado, que en este
    // mismo sitio ya costó dos intentos perdidos.
    const bytes = [];
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
          resolve(Buffer.from(bytes).toString("utf8"));
          return;
        }
        // El borrado quita un byte, que con un carácter multibyte a medio escribir dejaría basura;
        // pero es lo mismo que hace la terminal, y quien borra vuelve a teclear el carácter entero.
        if (byte === 127 || byte === 8) bytes.pop();
        else bytes.push(byte);
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


/**
 * Todos los productos del panel, recorriendo las páginas.
 *
 * Existía como `?tamano=200` a secas, en tres sitios y sin mirar `totalProductos`. Con 29
 * productos funcionaba; con 201 el que sobra desaparece **en silencio**, y lo que cuelga de esta
 * lista no es cosmético: la guarda que impide cargar dos veces el mismo producto, y el informe
 * de huérfanos, que daría por no reclamadas las fotos vivas de los que no vinieron.
 */
async function todosLosProductos() {
  const items = [];
  let pagina = 0;
  let totalPaginas = 1;
  do {
    const respuesta = await pedir(`/api/v1/admin/productos?tamano=200&pagina=${pagina}`);
    items.push(...respuesta.items);
    totalPaginas = respuesta.totalPaginas ?? 1;
    pagina++;
  } while (pagina < totalPaginas);
  return items;
}

/** El mismo tope que `Producto.TOPE_DE_GALERIA`. El que manda es el servidor; esto evita pedir
 * una URL firmada para un objeto que va a quedar huérfano cuando la confirmación responda 409. */
const TOPE_DE_GALERIA = 8;

/**
 * Lo que se sube de una toma, que **no es la maestra**.
 *
 * La maestra del estudio es un artefacto de archivo: 2000 px y medio megabyte. Hasta el 21 de
 * septiembre de 2026 era lo que llegaba al bucket, y en la primera medición de Lighthouse que
 * valió algo resultó ser el elemento más pesado de la portada **y** de la ficha, con 635 kB. La
 * variante web del mismo fotograma —AVIF, 1200 px— pesa 58.
 *
 * **Se niega en vez de caer a la maestra** si la variante no está. Caer sería volver al defecto
 * que esto corrige, y sin decir nada: la carga terminaría "bien" y el sitio seguiría pesando diez
 * veces lo que debe. El procesamiento del estudio no amplía, así que una toma sin variante es una
 * foto original demasiado pequeña, y eso se arregla con otra foto.
 *
 * El alto se deriva de la proporción de la maestra y no se lee del AVIF: leer su cabecera pide una
 * dependencia nueva para responder algo que ya se sabe. Comprobado contra el archivo real —las
 * maestras del estudio son cuadradas y el AVIF de 1200 mide 1200x1200—.
 */
function paraLaWeb(foto, dondeSeUsa) {
  if (!foto.variantes?.length) {
    throw new Error(
      `${dondeSeUsa}: la toma ${foto.ruta} no tiene variante web (AVIF hasta 1200 px).\n` +
        "No se sube la maestra en su lugar: son 635 kB donde caben 58. Reprocesa el estudio, o " +
        "la foto original es demasiado pequeña para publicarla.",
    );
  }
  const [mayor] = foto.variantes;
  return {
    // De mayor a menor, como vienen del estudio. La primera es la base: de ella salen el alto, el
    // hash y la URL que se sirve cuando el navegador no elige.
    variantes: foto.variantes,
    vistaPrevia: foto.vistaPrevia,
    ruta: mayor.ruta,
    contentType: mayor.contentType,
    ancho: mayor.ancho,
    alto: Math.round((foto.alto * mayor.ancho) / foto.ancho),
  };
}

/**
 * Sube las variantes de una toma y devuelve el cuerpo de la confirmación.
 *
 * Una URL firmada y un `PUT` por cada ancho, más el JPEG de la vista previa si el estudio lo dejó.
 * Son tres o cuatro viajes donde antes había uno, y es el precio de que el navegador pueda elegir:
 * la portada pedía 211 KiB de AVIF de 1200 px para pintarlos en huecos de 180.
 *
 * El hash y el alto son los de la variante mayor, que es la que el agregado toma como base.
 */
async function subirVariantes(endpointDeSubida, web, titulo) {
  const variantes = [];
  let hash = null;
  for (const variante of web.variantes) {
    const bytes = readFileSync(variante.ruta);
    const subida = await pedir(endpointDeSubida, {
      method: "POST",
      body: JSON.stringify({ contentType: variante.contentType }),
    });
    const puesta = await fetch(subida.url, {
      method: "PUT",
      headers: { "Content-Type": variante.contentType },
      body: bytes,
    });
    if (!puesta.ok) {
      throw new Error(
        `La subida de ${variante.ancho} px a Cloud Storage respondió ${puesta.status}`,
      );
    }
    variantes.push({ ancho: variante.ancho, objectKey: subida.objectKey });
    if (variante.ancho === web.ancho) {
      hash = createHash("sha256").update(bytes).digest("hex");
    }
  }

  let objectKeyVistaPrevia = null;
  if (web.vistaPrevia) {
    const bytes = readFileSync(web.vistaPrevia.ruta);
    const subida = await pedir(endpointDeSubida, {
      method: "POST",
      body: JSON.stringify({ contentType: web.vistaPrevia.contentType }),
    });
    const puesta = await fetch(subida.url, {
      method: "PUT",
      headers: { "Content-Type": web.vistaPrevia.contentType },
      body: bytes,
    });
    if (!puesta.ok) {
      throw new Error(`La subida de la vista previa respondió ${puesta.status}`);
    }
    objectKeyVistaPrevia = subida.objectKey;
  }

  return {
    variantes,
    objectKeyVistaPrevia,
    alto: web.alto,
    hash,
    altEs: `${titulo} sobre fondo gris`,
    altEn: `${titulo} on a grey background`,
  };
}

/**
 * Sube un archivo a la galería del producto: URL firmada, PUT a Cloud Storage, confirmación.
 * Los mismos tres pasos de la imagen principal, contra el subrecurso `galeria`.
 */
async function subirAGaleria(productoId, foto, titulo) {
  const web = paraLaWeb(foto, "galería");
  const cuerpo = await subirVariantes(
    `/api/v1/admin/productos/${productoId}/galeria/url-subida`,
    web,
    titulo,
  );
  return pedir(`/api/v1/admin/productos/${productoId}/galeria`, {
    method: "POST",
    body: JSON.stringify(cuerpo),
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
  const web = foto ? paraLaWeb(foto, producto.id) : null;
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

  await pedir(`/api/v1/admin/productos/${creado.id}/imagen-principal`, {
    method: "POST",
    body: JSON.stringify(
      await subirVariantes(
        `/api/v1/admin/productos/${creado.id}/imagen-principal/url-subida`,
        web,
        producto.titulo,
      ),
    ),
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
// `filter(Boolean)`: una carga interrumpida antes de crear la variante deja una entrada sin SKU
// —es el escenario que el orden de `cargarUno` busca a propósito—, y sin esto la corrida grande se
// llena de `FALLÓ undefined`.
const aRellenarGaleria = bandera("--galeria-todos")
  ? Object.values(leerJson(REGISTRO) ?? {})
      .map((anotado) => anotado.sku)
      .filter(Boolean)
  : valores("--galeria");
const RECONCILIAR = bandera("--reconciliar");
const REHACER_IMAGENES = bandera("--rehacer-imagenes");
const pedidos = bandera("--listos")
  ? productos.filter((p) => p.faltas.length === 0).map((p) => p.id)
  : (valor("--ids") ?? "").split(",").filter(Boolean);

if (
  pedidos.length === 0 &&
  mediciones.length === 0 &&
  aPublicar.length === 0 &&
  aRellenarGaleria.length === 0 &&
  !RECONCILIAR &&
  !REHACER_IMAGENES
) {
  console.error(
    "Hay que decir qué hacer: --ids a,b,c, --listos, --medir SKU=peso,largo,ancho,alto," +
      " --publicar-sku SKU, --galeria SKU, --galeria-todos, --rehacer-imagenes o --reconciliar.",
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
    corregidas++;
    if (!ESCRIBIR) continue;
    await pedir(`/api/v1/admin/variantes/${variante.varianteId}/paquete`, {
      method: "PATCH",
      body: JSON.stringify({ pesoGramos, largoCm, anchoCm, altoCm }),
    });
  }
  console.log(`\n${ESCRIBIR ? "corregidas" : "se corregirían"}: ${corregidas}`);
}

// El registro se lee antes de cualquier rama: publicar también lo escribe, no solo cargar.
const registro = leerJson(REGISTRO) ?? {};

if (REHACER_IMAGENES) {
  if (!TOKEN) {
    console.error(
      "Rehacer las imágenes es preguntarle al catálogo qué tiene, así que necesita sesión hasta" +
        " para simularlo. Usa --correo <correo> o --token <jwt>.",
    );
    process.exit(1);
  }
  await rehacerImagenes(registro);
  process.exit(process.exitCode ?? 0);
}

if (RECONCILIAR) {
  if (!TOKEN) {
    console.error(
      "Reconciliar es preguntarle al catálogo qué tiene, así que necesita sesión hasta para" +
        " simularlo. Usa --correo <correo> o --token <jwt>.",
    );
    process.exit(1);
  }
  await reconciliar(registro);
  process.exit(process.exitCode ?? 0);
}

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
  let fallaronGalerias = 0;

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
    const yaTiene = (detalle.galeria ?? []).length;
    const disponibles = fotosDeGaleria(producto).length;
    if (yaTiene > 0) {
      // Con menos de las que hay, no es "ya está": es una corrida que se cortó a mitad. El salto
      // silencioso convertía eso en un mensaje que se lee como éxito y las tomas que faltaban no
      // se subían nunca más. No se completa sola —no hay forma de saber cuál de las de allá
      // corresponde a cuál de las de aquí— pero se dice, que es lo que faltaba.
      const incompleta = yaTiene < disponibles;
      console.log(
        `${incompleta ? "INCOMPLETA " : "saltado    "} ${sku}: ${variante.nombreProducto} tiene` +
          ` ${yaTiene} de ${disponibles} imagen(es) de galería` +
          (incompleta ? " — revisar a mano: una corrida anterior no terminó" : ""),
      );
      if (incompleta) {
        process.exitCode = 1;
      }
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
    subidas += fotos.length;
    if (!ESCRIBIR) continue;

    let subidasDeEste = 0;
    try {
      for (const foto of fotos) {
        await subirAGaleria(variante.productoId, foto, producto.titulo);
        subidasDeEste++;
      }
    } catch (error) {
      // Sin este `catch`, un 500 o un token vencido a mitad propagaba fuera del bucle: moría el
      // proceso, no se imprimía ninguna línea de resumen y no había forma de saber cuáles de los
      // doce habían quedado hechos.
      console.error(
        `FALLÓ       ${sku}: ${error.message} (subió ${subidasDeEste} de ${fotos.length})`,
      );
      fallaronGalerias++;
      process.exitCode = 1;
    }
    if (id && subidasDeEste > 0) {
      registro[id] = { ...registro[id], imagenesDeGaleria: subidasDeEste };
      writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}
`, "utf8");
    }
  }
  console.log(`
${ESCRIBIR ? "subidas" : "se subirían"}: ${subidas}${fallaronGalerias > 0 ? ` · fallaron: ${fallaronGalerias}` : ""}`);
}

/**
 * Anota en el registro lo que el catálogo ya tiene y el registro no sabe. Sale temprano como las
 * otras: no carga nada, solo deja de mentir.
 *
 * <p>Casa por las <b>mismas dos guardas</b> que usa la carga para no duplicar, y en el mismo
 * orden: el SKU, que es una regla mecánica sobre el id de la lista y por tanto recalculable; y si
 * no, el nombre, que es el que atrapa lo que cargó cualquier otra cosa con otra regla de SKU
 * —`jbl-extreme-4` quedó publicado como `jbl-xtreme-4`—. Lo que no case con ninguna de las dos
 * no se adivina: se cuenta aparte y se dice que no está cargado.
 *
 * <p>Anota el SKU <b>del catálogo</b> y no el que tocaría por la regla, porque es el que
 * `--galeria-todos` y `--publicar-sku` van a usar para volver a encontrar el producto.
 */
/**
 * Vuelve a subir las imágenes de todo lo que el registro conoce, con la variante web de hoy.
 *
 * <p><b>El orden importa y es el contrario del obvio.</b> Primero se suben las nuevas y solo
 * después se borran las viejas: al revés, una corrida que se corte a la mitad —un 429, la red, un
 * Ctrl+C— deja el producto publicado y sin una sola foto. Así, lo peor que puede pasar es que
 * queden las dos tandas y sobren unas cuantas de galería, que se ve a simple vista y se arregla
 * volviendo a correr esto.
 *
 * <p>La principal no necesita borrado: su endpoint reemplaza (adr/0052). La galería acumula, así
 * que sus viejas hay que quitarlas una por una, y eso además borra el objeto del bucket.
 */
async function rehacerImagenes(registro) {
  const anotados = Object.entries(registro);
  let principales = 0;
  let deGaleria = 0;
  let borradas = 0;
  let fallaron = 0;

  for (const [id, anotado] of anotados) {
    const producto = porId.get(id);
    if (!producto) {
      console.error(`FALLÓ       ${anotado.sku}: el registro lo anota pero no está en la lista`);
      process.exitCode = 1;
      fallaron++;
      continue;
    }

    let web;
    try {
      web = paraLaWeb(producto.foto.archivos[0], anotado.sku);
    } catch (error) {
      console.error(`FALLÓ       ${anotado.sku}: ${error.message.split("\n")[0]}`);
      process.exitCode = 1;
      fallaron++;
      continue;
    }

    const detalle = await pedir(`/api/v1/admin/productos/${anotado.productoId}`);
    const viejas = (detalle.galeria ?? []).map((imagen) => imagen.id);
    const tomas = fotosDeGaleria(producto);
    const pesoWeb = readFileSync(web.ruta).length;

    console.log(
      `${ESCRIBIR ? "rehaciendo" : "simulado  "}  ${detalle.nombre ?? anotado.slug}\n` +
        `            ${anotado.sku} · principal ${web.variantes.map((v) => v.ancho).join("/")}px` +
        ` (mayor ${Math.round(pesoWeb / 1024)} kB)${web.vistaPrevia ? " + vista previa" : ""}` +
        ` · galería ${tomas.length} nuevas, ${viejas.length} a borrar`,
    );
    // Se cuenta antes del corte de la simulación, no después. Contar dentro de la rama que
    // escribe deja el resumen diciendo "0" debajo de las líneas que acaban de enumerar lo que
    // haría, y ese resumen es justo lo que se mira para decidir si vale la pena correrlo de
    // verdad. Este proyecto ya lo pagó tres veces, y una de ellas en este mismo archivo.
    principales++;
    deGaleria += tomas.length;
    borradas += viejas.length;
    if (!ESCRIBIR) continue;

    await pedir(`/api/v1/admin/productos/${anotado.productoId}/imagen-principal`, {
      method: "POST",
      body: JSON.stringify(
        await subirVariantes(
          `/api/v1/admin/productos/${anotado.productoId}/imagen-principal/url-subida`,
          web,
          producto.titulo,
        ),
      ),
    });

    for (const toma of tomas) {
      await subirAGaleria(anotado.productoId, toma, producto.titulo);
    }

    for (const imagenId of viejas) {
      await pedir(`/api/v1/admin/productos/${anotado.productoId}/galeria/${imagenId}`, {
        method: "DELETE",
      });
    }

    anotado.imagenesDeGaleria = tomas.length;
    anotado.imagenesRehechasEn = new Date().toISOString();
    writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}
`, "utf8");
  }

  console.log(
    `\n${ESCRIBIR ? "rehechas" : "se reharían"}: ${principales} principal(es) y ${deGaleria} de` +
      ` galería · ${borradas} vieja(s) borrada(s)` +
      (fallaron > 0 ? ` · ${fallaron} sin variante web` : ""),
  );
  if (!ESCRIBIR) {
    console.log("\nNada de esto pasó: falta --escribir.");
  }
}

async function reconciliar(registro) {
  const existencias = (await pedir("/api/v1/admin/variantes/existencias")).items;
  const porSku = new Map(existencias.map((v) => [v.sku, v]));
  const porProductoId = new Map(existencias.map((v) => [v.productoId, v]));
  const porNombre = new Map(
    (await todosLosProductos()).map((p) => [
      p.nombre.toLowerCase(),
      p,
    ]),
  );

  let yaEstaban = 0;
  let sinRastro = 0;

  // Primera pasada, sin escribir nada: quién casa con quién. Va aparte de la segunda porque dos
  // ids de la lista que casen con el mismo producto del catálogo invalidan **las dos**
  // coincidencias, y eso solo se sabe después de mirarlas todas. Anotar la primera y rechazar la
  // segunda dejaría escrita justo la que no se puede comprobar.
  const casados = [];
  for (const producto of productos) {
    if (registro[producto.id]) {
      yaEstaban++;
      continue;
    }
    const porElSku = porSku.get(skuDe(producto.id));
    const porElNombre = porNombre.get(producto.titulo.toLowerCase());
    const productoId = porElSku?.productoId ?? porElNombre?.id;
    if (!productoId) {
      sinRastro++;
      continue;
    }
    casados.push({ producto, productoId, porElSku });
  }

  // Los productoId que ya están en el registro también compiten: uno de ellos casando otra vez
  // significa que el registro ya dice que ese producto es otro id de la lista.
  const cuantos = new Map();
  for (const anotado of Object.values(registro)) {
    cuantos.set(anotado.productoId, (cuantos.get(anotado.productoId) ?? 0) + 1);
  }
  for (const { productoId } of casados) {
    cuantos.set(productoId, (cuantos.get(productoId) ?? 0) + 1);
  }

  const limpios = [];
  for (const casado of casados) {
    if (cuantos.get(casado.productoId) > 1) {
      const rivales = casados
        .filter((otro) => otro.productoId === casado.productoId && otro !== casado)
        .map((otro) => otro.producto.id);
      console.error(
        `FALLÓ       ${casado.producto.id}: casa con el mismo producto del catálogo que` +
          ` ${rivales.length > 0 ? rivales.join(", ") : "algo que el registro ya anotó"}.` +
          " Uno de los dos está mal y no es este script quien puede decidir cuál, así que no se" +
          " anota ninguno.",
      );
      process.exitCode = 1;
      continue;
    }
    limpios.push(casado);
  }

  // Segunda pasada: el detalle solo se pide de lo que va a quedar anotado.
  let anotados = 0;
  for (const { producto, productoId, porElSku } of limpios) {
    const variante = porElSku ?? porProductoId.get(productoId);
    const detalle = await pedir(`/api/v1/admin/productos/${productoId}`);
    const enGaleria = (detalle.galeria ?? []).length;
    const esperando = fotosDeGaleria(producto).length;
    console.log(
      `${ESCRIBIR ? "anotando" : "simulado"}    ${detalle.nombre}` +
        ` (${porElSku ? "por su SKU" : "por su nombre"})\n` +
        `            ${variante?.sku ?? "sin variante"} · ${detalle.estado}` +
        ` · ${enGaleria} en galería` +
        (enGaleria === 0 && esperando > 0 ? ` · ${esperando} toma(s) esperando en el estudio` : ""),
    );
    // Se cuenta lo que se haría, escriba o no, como en la carga: un resumen que dice "0" debajo
    // de tres líneas que dicen "simulado" hace dudar de las tres líneas.
    anotados++;
    if (!ESCRIBIR) continue;

    registro[producto.id] = {
      productoId,
      slug: detalle.slug,
      sku: variante?.sku ?? null,
      reconciliadoEn: new Date().toISOString(),
      publicado: detalle.estado === "PUBLICADO",
      imagenesDeGaleria: enGaleria,
    };
    writeFileSync(REGISTRO, `${JSON.stringify(registro, null, 2)}\n`, "utf8");
  }

  console.log(
    `\n${ESCRIBIR ? "anotados" : "se anotarían"}: ${anotados}` +
      ` · ya estaban: ${yaEstaban}` +
      ` · sin rastro en el catálogo: ${sinRastro}` +
      (ESCRIBIR ? `\nRegistro en ${REGISTRO}` : "\n\nNada de esto pasó: falta --escribir."),
  );
  console.log(
    "Los que no tienen rastro no están cargados: eso lo arregla --ids o --listos, no esto.",
  );
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
    publicados++;
    if (!ESCRIBIR) continue;
    await pedir(`/api/v1/admin/productos/${variante.productoId}/publicacion`, { method: "POST" });
    anotarPublicado(sku);
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
      (await todosLosProductos()).map((p) => p.nombre.toLowerCase()),
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
// Un pie que suma cargados y saltados y calla los fallos no cuadra con las líneas de arriba:
// trece pedidos con cinco caídos por un token vencido remataban en "cargados: 8 · saltados: 0".
let fallaron = 0;
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
    fallaron++;
    process.exitCode = 1;
  }
}

console.log(
  `\n${ESCRIBIR ? "cargados" : "se cargarían"}: ${cargados} · saltados: ${saltados}` +
    (fallaron > 0 ? ` · fallaron: ${fallaron}` : "") +
    (ESCRIBIR ? `\nRegistro en ${REGISTRO}` : "\n\nNada de esto pasó: falta --escribir."),
);
