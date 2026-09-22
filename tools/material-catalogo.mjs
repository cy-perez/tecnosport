// Lo que hay en `catalogo/` para cada producto de la lista del proveedor, y si alcanza para
// publicarlo. Vive aparte porque lo usan dos scripts —el cruce que informa y el cargador que
// escribe—, y dos definiciones de "publicable" que puedan divergir es exactamente el defecto
// que este proyecto se cansó de encontrar en otras partes.

import { readFileSync, readdirSync, existsSync, openSync, readSync, closeSync } from "node:fs";
import { basename, join } from "node:path";
import { fileURLToPath } from "node:url";

export const LADO_MINIMO = 1200;

/**
 * Lo que queda sobre la **venta** después del costo, en tanto por ciento.
 *
 * Vive aquí y no en cada script por la misma razón que `faltas`: estaba en los dos y con dos
 * fórmulas distintas. El cruce comparaba `venta <= costo * 1.05` —margen sobre el costo— y el
 * cargador `(venta - costo) / venta` —margen sobre la venta—, que no son el mismo umbral: entre
 * 4,76 % y 5,00 % sobre la venta, el informe daba el producto por bueno y el cargador lo
 * descartaba sin que nada lo hubiera anunciado. Es la divergencia que este módulo existe para
 * evitar, un nivel más abajo de donde se buscó.
 */
export function margenDe(producto) {
  const costo = producto.precio_proveedor_cop;
  const venta = producto.precio_mercado_cop;
  return costo && venta ? ((venta - costo) / venta) * 100 : null;
}

/** Por debajo de esto se trabaja gratis. Lo usan el informe y el filtro del cargador. */
export const MARGEN_MINIMO_SUGERIDO = 5;

// `fileURLToPath` y no `.pathname`, que viene percent-encoded: con el repositorio bajo
// `D:/Mis Proyectos/` o `C:/Users/José/`, `RAIZ` salía con `%20` dentro, `CATALOGO` apuntaba a una
// carpeta inexistente y el error que se veía era "esa carpeta no está versionada", que manda a
// buscar justo donde no es. `verificar-kit.mjs` ya lo hacía bien.
export const RAIZ = fileURLToPath(new URL("..", import.meta.url));
export const CATALOGO = join(RAIZ, "catalogo");
const ESTUDIO = join(CATALOGO, "fotos", "estudio");

/**
 * El ancho y el alto de un JPEG, leyendo su cabecera. Son ~25 líneas y evita una dependencia de
 * imágenes para responder una sola pregunta; la regla dura #9 de CLAUDE.md pesa más que la
 * comodidad de un `import`.
 */
export function dimensionesJpeg(ruta) {
  const fd = openSync(ruta, "r");
  try {
    const cabecera = Buffer.alloc(2);
    readSync(fd, cabecera, 0, 2, 0);
    if (cabecera[0] !== 0xff || cabecera[1] !== 0xd8) return null; // no es JPEG
    let posicion = 2;
    const marcador = Buffer.alloc(4);
    for (;;) {
      if (readSync(fd, marcador, 0, 4, posicion) < 4) return null;
      if (marcador[0] !== 0xff) return null;
      const tipo = marcador[1];
      const largo = marcador.readUInt16BE(2);
      // SOF0..SOF15 llevan el tamaño; 0xc4 (DHT), 0xc8 (JPG) y 0xcc (DAC) caen en ese rango y no
      // son marcos de imagen.
      if (tipo >= 0xc0 && tipo <= 0xcf && tipo !== 0xc4 && tipo !== 0xc8 && tipo !== 0xcc) {
        const marco = Buffer.alloc(5);
        readSync(fd, marco, 0, 5, posicion + 4);
        return { alto: marco.readUInt16BE(1), ancho: marco.readUInt16BE(3) };
      }
      posicion += 2 + largo;
    }
  } finally {
    closeSync(fd);
  }
}

export function leerJson(ruta) {
  return existsSync(ruta) ? JSON.parse(readFileSync(ruta, "utf8")) : null;
}

/**
 * Los anchos de la escala del estudio que sirven para la web, del mayor al menor.
 *
 * <p>**Tope en 1200 y no en 2000 a propósito.** La tarjeta de la rejilla pinta la foto a menos de
 * 400 px en un teléfono y la ficha no llega a 700; mandar 2000 es pagar ancho de banda por píxeles
 * que nadie ve. Se baja de ahí solo cuando la foto original no daba para más: el procesamiento del
 * estudio no amplía, así que un producto cuya toma venía a 600 px no tiene carpeta de 1200.
 */
const ANCHOS_WEB = [1200, 800, 600, 480];

/**
 * La variante web de una toma: el AVIF más grande disponible hasta 1200.
 *
 * <p>Se busca por el nombre del archivo de la maestra, no por posición, para que el orden de las
 * dos listas no pueda desalinearse en silencio. Si una toma no tiene ninguna variante, devuelve
 * `null` y quien suba **tiene que negarse**: subir la maestra en su lugar es exactamente el
 * defecto que esto viene a corregir, y en silencio.
 */
function variantePara(id, rutaMaestra) {
  const base = basename(rutaMaestra).replace(/\.jpg$/i, "");
  for (const ancho of ANCHOS_WEB) {
    const ruta = join(ESTUDIO, id, String(ancho), `${base}.avif`);
    if (existsSync(ruta)) return { ruta, ancho, contentType: "image/avif" };
  }
  return null;
}

/**
 * Las tomas de un producto: las maestras para juzgar y sus variantes web para subir.
 *
 * <p>**Son dos cosas distintas y por eso son dos listas.** La maestra es el artefacto de archivo:
 * es donde vive la resolución de verdad, y es la única que `dimensionesJpeg` sabe leer —el AVIF no
 * tiene una cabecera que se resuelva en veinticinco líneas—. La variante web es lo que se publica.
 * Confundirlas costó 635 kB por foto en la portada y en la ficha hasta el 21 de septiembre de
 * 2026.
 */
function fotos(id) {
  const carpeta = join(ESTUDIO, id, "maestra");
  if (!existsSync(carpeta)) return { archivos: [], ladoMenor: 0 };
  const archivos = readdirSync(carpeta)
    .filter((f) => f.toLowerCase().endsWith(".jpg"))
    .sort()
    .map((f) => ({ ruta: join(carpeta, f), ...dimensionesJpeg(join(carpeta, f)) }))
    .filter((f) => f.ancho)
    .map((f) => ({ ...f, web: variantePara(id, f.ruta) }));
  const lados = archivos.map((f) => Math.min(f.ancho, f.alto));
  return { archivos, ladoMenor: lados.length ? Math.min(...lados) : 0 };
}

/**
 * Fichas de Icecat que quedaron pegadas a **otro** producto. La búsqueda por nombre acierta casi
 * siempre y cuando falla no avisa: la ficha llega completa, con sus medidas, y se lee como buena.
 *
 * <p>Se listan aquí, con el motivo, en vez de confiar en un umbral inventado del tipo "un peso
 * menor de 200 g es sospechoso": lo que está mal no es la cifra, es de qué producto es.
 */
const FICHA_DE_OTRO_PRODUCTO = {
  "nintendo-switch-2-mario-kart":
    "la ficha es del juego Mario Kart World suelto —50 g en una caja de 17x11x2 cm—, no del " +
    "paquete con la consola. Declarar eso cobraría el flete de una tarjeta de juego para " +
    "despachar una consola de dos millones y medio.",
};

/**
 * Las cuatro cifras del empaque salen de la ficha de Icecat cuando el fabricante las publica. Los
 * de celulares no publican nada del empaque: ahí toca báscula, y eso no lo resuelve ningún script.
 *
 * <p>Se devuelven en gramos y centímetros enteros redondeados hacia arriba, que es lo que el
 * dominio exige, y solo si están las cuatro: tres medidas y un peso ausente no es "a medio medir",
 * es una carga rota (`adr/0021`, `adr/0046`).
 */
function empaque(id) {
  if (FICHA_DE_OTRO_PRODUCTO[id]) return null;
  const ficha = leerJson(join(CATALOGO, "icecat", "fichas", `${id}.json`));
  if (!ficha) return null;
  const especificaciones = (ficha.especificaciones ?? []).flatMap((grupo) =>
    Array.isArray(grupo.valores) ? grupo.valores : Array.isArray(grupo) ? grupo : [grupo],
  );
  const texto = (busca) =>
    especificaciones.find((e) => (e?.atributo ?? "").toLowerCase().includes(busca))?.valor ?? null;

  const aMilimetros = (valor) => {
    if (!valor) return null;
    const numero = Number.parseFloat(String(valor).replace(",", "."));
    if (Number.isNaN(numero)) return null;
    if (/\bcm\b/i.test(valor)) return numero * 10;
    if (/\bm\b/i.test(valor) && !/\bmm\b/i.test(valor)) return numero * 1000;
    return numero; // mm
  };
  const aGramos = (valor) => {
    if (!valor) return null;
    const numero = Number.parseFloat(String(valor).replace(",", "."));
    if (Number.isNaN(numero)) return null;
    return /\bkg\b/i.test(valor) ? numero * 1000 : numero;
  };

  const anchoMm = aMilimetros(texto("ancho del paquete"));
  const largoMm = aMilimetros(texto("profundidad del paquete"));
  const altoMm = aMilimetros(texto("altura del paquete"));
  const pesoG = aGramos(texto("peso del paquete"));
  if (!anchoMm || !largoMm || !altoMm || !pesoG) return null;

  return {
    pesoGramos: Math.ceil(pesoG),
    largoCm: Math.ceil(largoMm / 10),
    anchoCm: Math.ceil(anchoMm / 10),
    altoCm: Math.ceil(altoMm / 10),
  };
}

/**
 * Cada producto de la lista con su material y sus faltas. Las faltas son solo las que impiden
 * publicar: foto utilizable, prosa y precio. **Las medidas no son una falta** — sin ellas se
 * vende con recogida en el punto (`adr/0046`).
 */
export function leerMaterial() {
  const catalogo = leerJson(join(CATALOGO, "productos.json"));
  const prosa = leerJson(join(CATALOGO, "prosa.json"));
  if (!catalogo || !prosa) {
    throw new Error(
      "No encuentro catalogo/productos.json o catalogo/prosa.json. Esa carpeta no está versionada:\n" +
        "es material de trabajo y vive solo en la máquina donde se procesó la lista del proveedor.",
    );
  }

  const productos = catalogo.productos.map((producto) => {
    const foto = fotos(producto.id);
    const faltas = [];
    if (foto.archivos.length === 0) faltas.push("sin foto");
    else if (foto.ladoMenor < LADO_MINIMO) faltas.push(`foto ${foto.ladoMenor}px`);
    if (!prosa[producto.id]?.apertura) faltas.push("sin prosa");
    if (!producto.precio_mercado_cop) faltas.push("sin precio");
    return {
      ...producto,
      foto,
      empaque: empaque(producto.id),
      fichaDeOtroProducto: FICHA_DE_OTRO_PRODUCTO[producto.id] ?? null,
      prosa: prosa[producto.id],
      faltas,
    };
  });

  return { fecha: catalogo.fecha_lista, productos };
}
