#!/usr/bin/env node
// Dice qué productos de la lista del proveedor están listos para publicar y qué le falta a cada
// uno. No escribe nada en ninguna parte: solo informa. Quien carga es `cargar-catalogo.mjs`, y
// los dos leen el material por el mismo sitio (`material-catalogo.mjs`) para que no puedan tener
// dos ideas distintas de qué es "publicable".
//
// Las cuatro condiciones, y por qué son esas:
//
//   1. FOTO      la maestra más pequeña mide 1200 px o más. La ficha pinta ~570 px CSS, que en
//                una pantalla 2x son ~1140: por debajo de eso se ve borroso y no es publicable.
//   2. PROSA     descripción redactada en prosa.json. Sin ella la ficha queda muda.
//   3. PRECIO    precio de mercado, que es a lo que se vende. El del proveedor es el costo, y un
//                producto sin precio de mercado no se puede poner en la vitrina sin inventárselo.
//   4. MEDIDAS   las cuatro cifras del empaque. Sin ellas se puede vender, pero solo con
//                recogida en el punto (adr/0046): no hay cotización de envío.
//
// Una variante sin medir NO bloquea la publicación, y por eso se informa aparte y no como
// "falta". Lo que bloquea es foto, prosa o precio.
//
// El margen sale de comparar el costo con la venta y está a la vista a propósito: la lista tiene
// productos cuyo precio de mercado es igual o menor que lo que cuestan, y publicarlos así es
// vender a pérdida. Eso no lo puede decidir un script.
//
// Lo que este script NO dice es cuáles ya están publicados, y no es un olvido: el slug con el que
// se cargó un producto no siempre es su id aquí —`jbl-extreme-4` quedó como `jbl-xtreme-4`, y
// `samsung-a11-7-wifi-8gb-ram-128gb` como `samsung-galaxy-tab-a11-8-7-wifi-8gb-ram-128gb`—, así
// que cruzarlo por el nombre daría falsos "este es nuevo" con toda la confianza del mundo. Esa
// correspondencia la registra `cargar-catalogo.mjs` en catalogo/cargados.json desde ahora.
//
// Uso:  node tools/cruce-catalogo.mjs [--todos]
//       --todos  lista también los que no tienen ni una foto procesada.

import { leerMaterial } from "./material-catalogo.mjs";

const todos = process.argv.includes("--todos");

let material;
try {
  material = leerMaterial();
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

const filas = material.productos.filter((p) => todos || p.foto.archivos.length > 0);
const publicables = filas.filter((f) => f.faltas.length === 0);
const conEnvio = publicables.filter((f) => f.empaque);
const sinMargen = publicables.filter(
  (f) => f.precio_proveedor_cop && f.precio_mercado_cop <= f.precio_proveedor_cop * 1.05,
);

const pesos = (valor) => (valor ? valor.toLocaleString("es-CO") : "—");
console.log(`Lista del ${material.fecha} · ${material.productos.length} productos procesados`);
console.log(
  `${publicables.length} publicables · ${conEnvio.length} de ellos con medidas de empaque, ` +
    `los otros ${publicables.length - conEnvio.length} solo con recogida en el punto`,
);
console.log(
  "\nLas medidas salen de la ficha de Icecat. Que falten no impide publicar (adr/0046): impide\n" +
    "cotizar el envío, y eso se arregla con una báscula, no con un script.",
);
if (sinMargen.length > 0) {
  console.log(
    `\nOJO: ${sinMargen.length} publicable(s) dejan 5% o menos sobre el costo. Publicarlos a ese\n` +
      "precio es trabajar gratis o perder plata; hay que decidirlos uno por uno.",
  );
}
console.log("");

const ancho = Math.max(...filas.map((f) => f.id.length));
const ordenadas = filas.sort((a, b) => a.faltas.length - b.faltas.length || a.id.localeCompare(b.id));
for (const fila of ordenadas) {
  const costo = fila.precio_proveedor_cop;
  const venta = fila.precio_mercado_cop;
  const margen = costo && venta ? `${Math.round(((venta - costo) / venta) * 100)}%` : "—";
  console.log(
    [
      (fila.faltas.length === 0 ? "LISTO" : "FALTA").padEnd(6),
      fila.id.padEnd(ancho),
      `${fila.foto.archivos.length} foto`.padEnd(7),
      `${fila.foto.ladoMenor || "-"}px`.padEnd(8),
      `$${pesos(venta)}`.padEnd(12),
      `margen ${margen}`.padEnd(13),
      (fila.empaque ? "mide" : "sin medir").padEnd(10),
      fila.faltas.join(", "),
    ].join("  "),
  );
}
