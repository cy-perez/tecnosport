#!/usr/bin/env node
// Cruza el material del catálogo (catalogo/, que no está versionado) contra las cuatro
// condiciones que un producto tiene que cumplir para poder publicarse, y dice cuáles cumple
// cada uno. No escribe nada en ninguna parte: solo informa.
//
// Las cuatro condiciones, y por qué son esas:
//
//   1. FOTO      la maestra más pequeña mide 1200 px o más. La ficha pinta ~570 px CSS, que en
//                una pantalla 2x son ~1140: por debajo de eso se ve borroso y no es publicable.
//   2. PROSA     descripción redactada en prosa.json. Sin ella la ficha queda muda.
//   3. PRECIO    precio del proveedor y precio de mercado, que es lo que decide la ganancia.
//   4. MEDIDAS   las cuatro cifras del empaque. Sin ellas se puede vender, pero solo con
//                recogida en el punto (adr/0046): no hay cotización de envío.
//
// Una variante sin medir NO bloquea la publicación, y por eso se informa aparte y no como
// "falta". Lo que bloquea es foto, prosa o precio.
//
// Lo que este script NO dice es cuáles ya están publicados, y no es un olvido: el slug con el que
// se cargó un producto no siempre es su id aquí —`jbl-extreme-4` quedó como `jbl-xtreme-4`, y
// `samsung-a11-7-wifi-8gb-ram-128gb` como `samsung-galaxy-tab-a11-8-7-wifi-8gb-ram-128gb`—, así
// que cruzarlo automáticamente daría falsos "este es nuevo" con toda la confianza del mundo. Esa
// correspondencia la tiene que registrar quien carga (ver el cargador, pendiente).
//
// Uso:  node tools/cruce-catalogo.mjs [--todos]
//       --todos  lista también los que no tienen ni una foto procesada.

import { readFileSync, readdirSync, existsSync, openSync, readSync, closeSync } from "node:fs";
import { join } from "node:path";

const RAIZ = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const CATALOGO = join(RAIZ, "catalogo");
const ESTUDIO = join(CATALOGO, "fotos", "estudio");
const LADO_MINIMO = 1200;

/**
 * El lado menor de un JPEG, leyendo su cabecera. Son ~25 líneas y evita una dependencia de
 * imágenes para responder una sola pregunta; la regla dura #9 de CLAUDE.md pesa más que la
 * comodidad de un `import`.
 */
function ladoMenorJpeg(ruta) {
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
      // SOF0..SOF15 llevan el tamaño; SOF4 (DHT) y SOF12 (DAC) no son SOF de verdad.
      if (tipo >= 0xc0 && tipo <= 0xcf && tipo !== 0xc4 && tipo !== 0xc8 && tipo !== 0xcc) {
        const marco = Buffer.alloc(5);
        readSync(fd, marco, 0, 5, posicion + 4);
        return Math.min(marco.readUInt16BE(1), marco.readUInt16BE(3));
      }
      posicion += 2 + largo;
    }
  } finally {
    closeSync(fd);
  }
}

function leerJson(ruta) {
  return existsSync(ruta) ? JSON.parse(readFileSync(ruta, "utf8")) : null;
}

function fotos(id) {
  const carpeta = join(ESTUDIO, id, "maestra");
  if (!existsSync(carpeta)) return { cuantas: 0, ladoMenor: 0 };
  const archivos = readdirSync(carpeta).filter((f) => f.toLowerCase().endsWith(".jpg"));
  const lados = archivos.map((f) => ladoMenorJpeg(join(carpeta, f))).filter((l) => l);
  return { cuantas: lados.length, ladoMenor: lados.length ? Math.min(...lados) : 0 };
}

/**
 * Las cuatro cifras del empaque salen de la ficha de Icecat cuando el fabricante las publica.
 * Los de celulares no publican nada del empaque: ahí toca báscula, y eso no lo resuelve ningún
 * script.
 */
function medidas(id) {
  const ficha = leerJson(join(CATALOGO, "icecat", "fichas", `${id}.json`));
  if (!ficha) return null;
  const especificaciones = (ficha.especificaciones ?? []).flatMap((grupo) =>
    Array.isArray(grupo.valores) ? grupo.valores : Array.isArray(grupo) ? grupo : [grupo],
  );
  const buscar = (texto) =>
    especificaciones.find((e) => (e?.atributo ?? "").toLowerCase().includes(texto))?.valor ?? null;
  const cuatro = {
    ancho: buscar("ancho del paquete"),
    profundidad: buscar("profundidad del paquete"),
    alto: buscar("altura del paquete"),
    peso: buscar("peso del paquete"),
  };
  return Object.values(cuatro).every(Boolean) ? cuatro : null;
}

const todos = process.argv.includes("--todos");
const catalogo = leerJson(join(CATALOGO, "productos.json"));
const prosa = leerJson(join(CATALOGO, "prosa.json"));
if (!catalogo || !prosa) {
  console.error(
    "No encuentro catalogo/productos.json o catalogo/prosa.json. Esa carpeta no está versionada:\n" +
      "es material de trabajo y vive solo en la máquina donde se procesó la lista del proveedor.",
  );
  process.exit(1);
}

const filas = catalogo.productos
  .map((producto) => {
    const foto = fotos(producto.id);
    const empaque = medidas(producto.id);
    const faltas = [];
    if (foto.cuantas === 0) faltas.push("sin foto");
    else if (foto.ladoMenor < LADO_MINIMO) faltas.push(`foto ${foto.ladoMenor}px`);
    if (!prosa[producto.id]?.apertura) faltas.push("sin prosa");
    if (!producto.precio_proveedor_cop) faltas.push("sin precio");
    return {
      id: producto.id,
      marca: producto.marca,
      titulo: producto.titulo ?? producto.modelo,
      precio: producto.precio_proveedor_cop,
      mercado: producto.precio_mercado_cop,
      colores: (producto.colores_oficiales ?? []).length,
      foto,
      empaque,
      faltas,
    };
  })
  .filter((fila) => todos || fila.foto.cuantas > 0);

const publicables = filas.filter((f) => f.faltas.length === 0);
const conEnvio = publicables.filter((f) => f.empaque);

const pesos = (valor) => (valor ? valor.toLocaleString("es-CO") : "—");
console.log(`Lista del ${catalogo.fecha_lista} · ${catalogo.productos.length} productos procesados`);
console.log(
  `${publicables.length} publicables · ${conEnvio.length} de ellos con medidas de empaque, ` +
    `los otros ${publicables.length - conEnvio.length} solo con recogida en el punto`,
);
console.log(
  "\nLas medidas salen de la ficha de Icecat. Que falten no impide publicar (adr/0046): impide\n" +
    "cotizar el envío, y eso se arregla con una báscula, no con un script.\n",
);

const ancho = Math.max(...filas.map((f) => f.id.length));
for (const fila of filas.sort((a, b) => a.faltas.length - b.faltas.length || a.id.localeCompare(b.id))) {
  const estado = fila.faltas.length === 0 ? "LISTO" : "FALTA";
  console.log(
    [
      estado.padEnd(6),
      fila.id.padEnd(ancho),
      `${fila.foto.cuantas} foto`.padEnd(7),
      `${fila.foto.ladoMenor || "-"}px`.padEnd(8),
      `$${pesos(fila.precio)}`.padEnd(12),
      (fila.empaque ? "mide" : "sin medir").padEnd(10),
      `${fila.colores} color(es)`.padEnd(13),
      fila.faltas.join(", "),
    ].join("  "),
  );
}
