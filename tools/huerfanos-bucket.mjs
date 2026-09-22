#!/usr/bin/env node
// ¿Cuántos objetos del bucket de imágenes no los reclama nadie?
//
// **Informa; no borra.** Y no es prudencia de más: la nota que pedía esto proponía resolverlo con
// una regla de ciclo de vida "sobre el prefijo `galeria-`", y esa regla **no se puede escribir**.
// La key es `productos/{id}/galeria-{uuid}.ext` y el `matchesPrefix` de Cloud Storage compara
// desde el principio del nombre: lo único prefijable ahí es `productos/`, y una regla por
// antigüedad sobre eso borraría las fotos vivas — la del producto publicado hace seis meses es
// justo la más vieja. Con el número delante se decide si vale la pena cambiar la forma de las
// keys (lo no confirmado a `pendientes/`, y entonces sí una regla trivial y segura) o si esto no
// pesa lo suficiente para tocar nada.
//
// De dónde sale cada cosa:
//
//   - El bucket, de `gcloud storage ls`. Sin dependencias nuevas: la alternativa era el SDK de
//     Cloud Storage, y cada librería nueva es deuda.
//   - Lo referenciado, del panel: la imagen principal de la lista y la galería de cada ficha.
//     Del panel y no del catálogo público porque un borrador también tiene sus fotos subidas, y
//     desde fuera no se ven: darlas por huérfanas sería justo el error caro.
//
// Lo que este informe **no juzga** son los fotogramas de `rotacion/`: un set sin publicar no
// expone sus imágenes por ninguna API, así que desde aquí no hay forma de distinguir "es de un
// set en preparación" de "no lo reclama nadie". Se cuentan aparte y se dice por qué.
//
// Y desde ADR-0057 hay un segundo punto ciego, más grande: **una imagen se publica en varios
// anchos**. De la galería se reclaman todos, porque la ficha del panel los devuelve; de la
// **imagen principal** solo se reclama la variante mayor, porque `ProductoAdminDetalleRespuesta`
// expone un `imagenPrincipalUrl` y nada más. Mientras eso siga así, cada ancho pequeño y cada
// JPEG de vista previa de una principal aparece aquí como no reclamado **estando vivo y
// sirviéndose en el sitio**. Se avisa en el encabezado del informe, con todas las letras: quien
// borre a mano lo que esta lista enumera, sin leer el aviso, deja las fichas sin fotos.
//
// Uso:  node tools/huerfanos-bucket.mjs --bucket <nombre> --correo <correo>
//       node tools/huerfanos-bucket.mjs --bucket <nombre> --token <jwt> --api <url>
import { spawnSync } from "node:child_process";

const argv = process.argv.slice(2);
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

// Sin omisión, igual que `--bucket`, y por el mismo motivo: los dos lados del cruce tienen que
// ser deliberados. Con `http://localhost:8080` por omisión, olvidar `--api` con el `bootRun`
// levantado —el estado normal de esta máquina— cruzaba el bucket que se pidiera contra el
// catálogo local y daba por huérfano casi todo lo de allá. La mitad protegida no decidía nada.
const API = (valor("--api") ?? "").replace(/[/]$/, "");
const BUCKET = valor("--bucket", process.env.GCS_BUCKET_IMAGENES);
const CORREO = valor("--correo");
let TOKEN = valor("--token", process.env.TS_TOKEN_ADMIN);

// El nombre va dentro de `gs://${BUCKET}/productos/**` en una invocación con `shell: true`
// —que hace falta porque en Windows gcloud es un `.cmd`—, así que un valor con `&`, `|` o
// comillas ejecutaría lo que venga detrás. Las reglas de nombre de Cloud Storage no admiten
// ninguno de esos caracteres, de modo que validarlo no rechaza ningún bucket real.
if (BUCKET && !/^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$/.test(BUCKET)) {
  console.error(
    `"${BUCKET}" no tiene forma de nombre de bucket. Se comprueba porque el nombre se interpola` +
      " en una línea de shell.",
  );
  process.exit(1);
}

if (!API) {
  console.error(
    "Falta la API: --api <url>. Tampoco tiene omisión: el informe cruza lo que hay en el bucket" +
      " contra lo que el panel reclama, y con las dos mitades de ambientes distintos el resultado" +
      " es basura que se lee como un hallazgo.",
  );
  process.exit(1);
}

if (!BUCKET) {
  console.error(
    "Falta el bucket: --bucket <nombre> o la variable GCS_BUCKET_IMAGENES.\n" +
      "No hay ninguno por omisión a propósito: el nombre del bucket de producción y el de dev se" +
      " parecen lo bastante como para que equivocarse sea fácil.",
  );
  process.exit(1);
}

/** La clave, leída de la terminal sin eco. Copiada de `cargar-catalogo.mjs`, que la explica. */
function preguntarClave(pregunta) {
  return new Promise((resolve, reject) => {
    if (!process.stdin.isTTY) {
      reject(new Error("No hay terminal donde preguntar la clave. Usa --token."));
      return;
    }
    process.stdout.write(pregunta);
    process.stdin.setRawMode(true);
    process.stdin.resume();
    // Los bytes se juntan y se decodifican al final: una "ñ" llega en dos bytes y
    // `String.fromCharCode` por byte los convertía en dos caracteres distintos, así que la clave
    // que viajaba no era la tecleada y el 401 se explicaba como "clave incorrecta".
    const bytes = [];
    process.stdin.on("data", function escuchar(bloque) {
      for (const byte of bloque) {
        if (byte === 3) {
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
        if (byte === 127 || byte === 8) bytes.pop();
        else bytes.push(byte);
      }
    });
  });
}

async function pedir(ruta) {
  const respuesta = await fetch(`${API}${ruta}`, {
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${TOKEN}` },
  });
  if (!respuesta.ok) {
    throw new Error(`GET ${ruta} respondió ${respuesta.status}: ${await respuesta.text()}`);
  }
  return respuesta.json();
}

/**
 * La key dentro del bucket de una URL pública, o null si esa URL no apunta a nuestro bucket.
 *
 * Se busca `productos/` en vez de recortar por la base configurada, y eso resuelve dos cosas a la
 * vez: no hace falta pasarle aquí otra variable de entorno que tendría que coincidir con la del
 * servidor, y las imágenes sembradas de `picsum.photos` —que las hay en local y en dev— se
 * descartan solas por no contener ese tramo.
 */
function keyDe(url) {
  const corte = String(url ?? "").indexOf("productos/");
  return corte < 0 ? null : url.slice(corte);
}

const enMiB = (bytes) => `${(bytes / 1024 / 1024).toFixed(2)} MiB`;

if (CORREO && !TOKEN) {
  const clave = await preguntarClave(`Clave de ${CORREO}: `);
  const respuesta = await fetch(`${API}/api/v1/auth/sesion`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ correo: CORREO, clave }),
  });
  if (!respuesta.ok) {
    console.error(`El inicio de sesión respondió ${respuesta.status}.`);
    process.exit(1);
  }
  const sesion = await respuesta.json();
  if (sesion.rol !== "ADMIN" || !sesion.accessToken) {
    console.error(`Esa cuenta no sirve: rol ${sesion.rol}.`);
    process.exit(1);
  }
  TOKEN = sesion.accessToken;
}

if (!TOKEN) {
  console.error(
    "Falta el token del panel: --correo <correo>, --token <jwt> o la variable TS_TOKEN_ADMIN.\n" +
      "Hace falta porque un producto en BORRADOR también tiene sus fotos subidas y no se ven" +
      " desde fuera: sin sesión, todas parecerían huérfanas.",
  );
  process.exit(1);
}

// --- lo que hay en el bucket ---
// `shell: true` en Windows y no por gusto: ahí `gcloud` es un `.cmd`, no un ejecutable, y sin
// shell `spawnSync` falla con ENOENT y `stdout` sin definir — o sea, un error que no se parece
// en nada a "no tengo gcloud".
const listado = spawnSync(
  "gcloud",
  ["storage", "ls", "--long", "--recursive", `gs://${BUCKET}/productos/**`],
  { encoding: "utf8", shell: process.platform === "win32" },
);
if (listado.error || listado.status !== 0) {
  console.error(
    `No se pudo listar gs://${BUCKET}/productos/:\n` +
      (listado.error?.message ?? listado.stderr ?? "").trim() +
      "\n\n¿Está gcloud instalado y con sesión? `gcloud config get-value project` lo dice.",
  );
  process.exit(1);
}

const objetos = [];
for (const linea of listado.stdout.split("\n")) {
  // `    597252  2026-09-19T23:00:02Z  gs://bucket/productos/{id}/principal-{uuid}.jpg`
  const partes = linea.trim().match(/^(\d+)\s+(\S+)\s+gs:\/\/[^/]+\/(.+)$/);
  if (partes) {
    objetos.push({ bytes: Number(partes[1]), fecha: partes[2], key: partes[3] });
  }
}
// Una salida que no se pudo interpretar no es un bucket vacío, y las dos se veían igual: si
// gcloud cambia el formato de `--long`, o el proyecto no está seleccionado y responde otra cosa
// por stdout con código 0, todas las líneas se descartaban en silencio y el informe remataba con
// "no hay ningún objeto". Un parcial es peor todavía: cada línea perdida encoge el total, el
// tamaño y la lista de huérfanos, sin dejar rastro de que faltaba algo.
const lineasUtiles = listado.stdout
  .split("\n")
  .filter((linea) => linea.trim() !== "" && !/^TOTAL:/i.test(linea.trim()));
if (objetos.length !== lineasUtiles.length) {
  const sinInterpretar = lineasUtiles.filter(
    (linea) => !objetos.some((objeto) => linea.includes(objeto.key)),
  );
  console.error(
    `No entendí ${sinInterpretar.length} de ${lineasUtiles.length} líneas que devolvió gcloud.\n` +
      "El informe se calcularía sobre lo que sí se pudo leer, y cada objeto que falte se leería\n" +
      "como reclamado. Primera línea sin interpretar:\n  " +
      (sinInterpretar[0] ?? "").trim(),
  );
  process.exit(1);
}
if (objetos.length === 0) {
  console.log(`En gs://${BUCKET}/productos/ no hay ningún objeto.`);
  process.exit(0);
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

// --- lo que el catálogo reclama ---
const productos = await todosLosProductos();
const reclamadas = new Set();
for (const producto of productos) {
  const principal = keyDe(producto.imagenPrincipalUrl);
  if (principal) reclamadas.add(principal);
  const detalle = await pedir(`/api/v1/admin/productos/${producto.id}`);
  for (const imagen of detalle.galeria ?? []) {
    // Todas las variantes y la vista previa, no solo `url`: desde ADR-0057 una imagen son varios
    // objetos y reclamar uno solo daría por huérfanos a los demás, que están vivos.
    for (const variante of imagen.variantes ?? []) {
      const key = keyDe(variante.url);
      if (key) reclamadas.add(key);
    }
    const key = keyDe(imagen.url);
    if (key) reclamadas.add(key);
    const previa = keyDe(imagen.urlVistaPrevia);
    if (previa) reclamadas.add(previa);
  }
}

const deRotacion = objetos.filter((o) => o.key.includes("/rotacion/"));
const juzgables = objetos.filter((o) => !o.key.includes("/rotacion/"));
const huerfanos = juzgables.filter((o) => !reclamadas.has(o.key));
const sumar = (lista) => lista.reduce((total, o) => total + o.bytes, 0);
// La interseccion, no el tamaño del conjunto: `reclamadas` son las keys que el panel dice tener,
// y una fila que apunte a un objeto ya borrado inflaba el numero hasta poder salir mayor que la
// cantidad de objetos listados — "28 objetos · 31 reclamados".
const reclamadosPresentes = objetos.filter((o) => reclamadas.has(o.key)).length;

console.log(
  `gs://${BUCKET}/productos/ · ${objetos.length} objetos · ${enMiB(sumar(objetos))}\n` +
    `${productos.length} productos en el panel reclaman ${reclamadosPresentes} de ellos.\n`,
);

// El punto ciego de la imagen principal, dicho antes de la lista y no en una nota al pie: lo que
// se lee primero es lo que decide si alguien borra.
const principalesSinReclamar = huerfanos.filter((o) => o.key.includes("/principal-")).length;
if (principalesSinReclamar > 0) {
  console.log(
    `OJO: ${principalesSinReclamar} de los objetos de abajo son de 'principal-', y de una imagen\n` +
      "principal este informe solo sabe reclamar su variante mayor: la ficha del panel devuelve\n" +
      "un 'imagenPrincipalUrl' y no la lista de anchos (ADR-0057). Los demás anchos y el JPEG de\n" +
      "vista previa aparecen como no reclamados ESTANDO VIVOS. No borres nada de 'principal-'\n" +
      "con esta lista en la mano hasta que la API del panel exponga las variantes.\n",
  );
}

if (huerfanos.length === 0) {
  console.log("Ningún objeto de 'principal-' ni de 'galeria-' está sin reclamar.");
} else {
  const ordenados = [...huerfanos].sort((a, b) => a.fecha.localeCompare(b.fecha));
  console.log(`${huerfanos.length} sin reclamar · ${enMiB(sumar(huerfanos))}:\n`);
  for (const objeto of ordenados) {
    console.log(`  ${objeto.fecha}  ${String(objeto.bytes).padStart(8)}  ${objeto.key}`);
  }
  console.log(
    `\nEl más viejo es del ${ordenados[0].fecha.slice(0, 10)}. Cada uno es una subida firmada` +
      " que nunca se confirmó:\n el objeto quedó en el bucket y la fila nunca se creó.",
  );
}

if (deRotacion.length > 0) {
  console.log(
    `\nY ${deRotacion.length} fotogramas de 'rotacion/' (${enMiB(sumar(deRotacion))}) que este` +
      " informe no juzga:\nun set sin publicar no expone sus imágenes por ninguna API, así que" +
      " desde aquí no se distingue\nun set en preparación de un resto que nadie reclama.",
  );
}
