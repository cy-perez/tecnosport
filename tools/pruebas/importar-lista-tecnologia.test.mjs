// Lo que la herramienta le dice a quien importa: lo que va a mandar y lo que hizo la lista. Lo que
// importa que no se pierda es el aviso de margen —la lista no mueve el precio de venta— y los
// modelos que no se volvieron a proponer.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  cuerpoParaModo,
  informeDeImportacion,
  porProponerDe,
  resumenDeLista,
  huellaDeArchivo,
  mensajeDeListaCambiada,
  mismasFotos,
  nombreDeLaQueEntro,
  seleccionParaBorradores,
  TOPE_DE_FOTOS,
  tomasListas,
} from "../importar-lista-tecnologia.mjs";

test("el resumen cuenta modelos, configuraciones y desaparecidos", () => {
  const resumen = resumenDeLista({
    fechaLista: "2026-10-08",
    bloques: ["ANDROID"],
    modelos: [{ configuraciones: [{}, {}] }, { configuraciones: [{}] }],
    configuracionesDesaparecidas: ["a"],
    modelosDesaparecidos: [],
  });
  assert.match(resumen, /2026-10-08 \(ANDROID\)/);
  assert.match(resumen, /2 modelos y 3 configuraciones/);
  assert.match(resumen, /1 configuraciones y 0 modelos desaparecidos/);
});

test("el informe avisa lo que quedó sin margen y lo que ya estaba decidido", () => {
  const informe = informeDeImportacion({
    productosRenovados: 3,
    variantesRepuestas: 7,
    variantesRetiradas: 2,
    modelosAgotados: 1,
    borradoresNuevos: 4,
    borradoresActualizados: 0,
    modelosYaDecididos: ["JBL Go 4"],
    sinMargen: ["Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM · Negro"],
    coloresSinVariante: ["Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM · Azul"],
  });
  assert.match(informe, /3 productos renovados, 7 variantes repuestas/);
  assert.match(informe, /ya rechazados\): JBL Go 4/);
  assert.match(informe, /OJO, el costo alcanzó el precio de venta/);
  assert.match(informe, /- Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM · Negro/);
  assert.match(informe, /colores que el producto no tiene[\s\S]*· Azul/);
});

test("sin avisos no hay líneas de aviso", () => {
  const informe = informeDeImportacion({
    productosRenovados: 0,
    variantesRepuestas: 0,
    variantesRetiradas: 0,
    modelosAgotados: 0,
    borradoresNuevos: 1,
    borradoresActualizados: 0,
    modelosYaDecididos: [],
    sinMargen: [],
  });
  assert.doesNotMatch(informe, /OJO|rechazados/);
});

test("los borradores no mandan lo desaparecido y el inventario sí", () => {
  const lista = {
    fechaLista: "2026-10-10",
    modelos: [],
    configuracionesDesaparecidas: ["a17-256-1-sim"],
    modelosDesaparecidos: ["jbl-boombox-4"],
  };
  const inventario = cuerpoParaModo(lista, "INVENTARIO");
  assert.equal(inventario.modo, "INVENTARIO");
  assert.deepEqual(inventario.modelosDesaparecidos, ["jbl-boombox-4"]);
  const borradores = cuerpoParaModo(lista, "BORRADORES");
  assert.equal(borradores.modo, "BORRADORES");
  assert.deepEqual(borradores.configuracionesDesaparecidas, []);
  assert.deepEqual(borradores.modelosDesaparecidos, []);
  assert.throws(() => cuerpoParaModo(lista, "COMPLETO"), /Modo desconocido/);
});

test("el inventario entrega lo que espera borrador y lo dice", () => {
  const resultado = {
    productosRenovados: 40,
    variantesRepuestas: 90,
    variantesRetiradas: 3,
    modelosAgotados: 1,
    borradoresNuevos: 0,
    borradoresActualizados: 0,
    modelosYaDecididos: [],
    sinMargen: [],
    modelosPorProponer: ["xiaomi-poco-f9-ultra-5g", "apple-airpods-pro-3"],
    configuracionesPorProponer: ["samsung-galaxy-a17-5g-8gb-ram-256gb-dual-sim"],
  };
  assert.deepEqual(porProponerDe({ fechaLista: "2026-10-10" }, resultado, "h"), {
    fechaLista: "2026-10-10",
    huellaLista: "h",
    modelos: ["xiaomi-poco-f9-ultra-5g", "apple-airpods-pro-3"],
    configuraciones: ["samsung-galaxy-a17-5g-8gb-ram-256gb-dual-sim"],
  });
  assert.match(
    informeDeImportacion(resultado),
    /Esperan borrador: 2 modelos[\s\S]*y 1 configuraciones/,
  );
});

const LISTA = {
  fechaLista: "2026-10-10",
  modelos: [
    { idModelo: "poco-f9-ultra", titulo: "POCO F9 Ultra", configuraciones: [{ sku: "f9-512" }] },
    { idModelo: "airpods-pro-3", titulo: "AirPods Pro 3", configuraciones: [{ sku: "app3" }] },
    { idModelo: "honor-5i", titulo: "Honor 5i", configuraciones: [{ sku: "h5i" }] },
    {
      idModelo: "galaxy-a17",
      titulo: "Galaxy A17",
      configuraciones: [{ sku: "a17-1-sim" }, { sku: "a17-dual" }],
    },
    { idModelo: "jbl-flip-7", titulo: "JBL Flip 7", configuraciones: [{ sku: "flip7" }] },
  ],
};
const POR_PROPONER = {
  fechaLista: "2026-10-10",
  modelos: ["poco-f9-ultra", "airpods-pro-3", "honor-5i"],
  configuraciones: ["a17-dual"],
};

test("los borradores llevan los modelos aptos y las configuraciones nuevas, y dicen qué se queda", () => {
  const { lista, omitidos } = seleccionParaBorradores(LISTA, POR_PROPONER, {
    "poco-f9-ultra": { estado: "apto", listas: ["a.jpg"] },
    "airpods-pro-3": { estado: "en_espera", faltan: 2, motivo: "salieron 2 fotos LISTA" },
    "honor-5i": { estado: "fuera_de_tanda", motivo: "sacado de la tanda: sin material" },
  });
  assert.deepEqual(
    lista.modelos.map((m) => [m.idModelo, m.configuraciones.map((c) => c.sku)]),
    [
      ["poco-f9-ultra", ["f9-512"]],
      ["galaxy-a17", ["a17-dual"]],
    ],
    "el JBL ya se vende sin nada nuevo: se renovó con el inventario",
  );
  assert.deepEqual(
    omitidos.map((o) => o.titulo),
    ["AirPods Pro 3", "Honor 5i"],
  );
  assert.match(omitidos[0].motivo, /espera fotos/);
  assert.match(omitidos[1].motivo, /fuera de la tanda/);
});

test("un modelo nuevo que no pasó por la tanda no entra", () => {
  const { lista, omitidos } = seleccionParaBorradores(LISTA, POR_PROPONER, {});
  assert.deepEqual(
    lista.modelos.map((m) => m.idModelo),
    ["galaxy-a17"],
  );
  assert.equal(omitidos.length, 3);
  assert.match(omitidos[0].motivo, /no pasó por la tanda/);
});

test("las tomas que se suben son las LISTA, en el orden de la tanda", () => {
  const archivos = [
    { ruta: "/f/Fotos procesadas/x_1.jpg" },
    { ruta: "/f/Fotos procesadas/x_2.jpg" },
    { ruta: "/f/Fotos procesadas/x_3.jpg" },
  ];
  assert.deepEqual(
    tomasListas(archivos, ["x_3.jpg", "x_1.jpg"]).map((a) => a.ruta),
    ["/f/Fotos procesadas/x_3.jpg", "/f/Fotos procesadas/x_1.jpg"],
  );
  assert.throws(() => tomasListas(archivos, ["x_9.jpg"]), /ya no está/);
});

test("nunca se suben más fotos de las que caben en el borrador", () => {
  const archivos = Array.from({ length: 12 }, (_, i) => ({ ruta: `/f/x_${i + 1}.jpg` }));
  const listas = archivos.map((a) => a.ruta.slice(3));
  assert.equal(tomasListas(archivos, listas).length, TOPE_DE_FOTOS);
});

test("las fotos del borrador son las mismas solo con los mismos hashes en el mismo orden", () => {
  assert.equal(mismasFotos(["a", "b"], ["a", "b"]), true);
  assert.equal(mismasFotos(["a", "b"], ["b", "a"]), false, "cambió la principal");
  assert.equal(mismasFotos(["a", "b"], ["a", "c"]), false, "otra tanda, la misma cantidad");
  assert.equal(mismasFotos(["a", "b"], ["a"]), false);
});

test("la huella del archivo cambia si cambia la lista", () => {
  assert.equal(huellaDeArchivo("{}"), huellaDeArchivo("{}"));
  assert.notEqual(huellaDeArchivo("{}"), huellaDeArchivo("{ }"));
});

test("la copia de lo que entró se nombra por la fecha y la huella", () => {
  const huella = huellaDeArchivo('{"fechaLista":"2026-10-15"}');
  const nombre = nombreDeLaQueEntro("2026-10-15", huella);
  assert.equal(nombre, `entro-2026-10-15-${huella.slice(0, 12)}.json`);
  // Dos exportaciones del mismo día no comparten copia: la segunda no pisa a la primera.
  assert.notEqual(nombre, nombreDeLaQueEntro("2026-10-15", huellaDeArchivo("{}")));
});

test("con la copia de lo que entró, la negativa dice con qué archivo correr los borradores", () => {
  const mensaje = mensajeDeListaCambiada("5-api/lista-api.json", "5-api/entro-2026-10-15-abc.json");
  assert.match(mensaje, /lista-api\.json cambió después de que su inventario entró/);
  assert.match(mensaje, /Lo que entró está en 5-api\/entro-2026-10-15-abc\.json/);
  assert.doesNotMatch(mensaje, /Vuelve a exportar/);
});

test("sin la copia, la negativa manda a exportar otra vez o a correr el inventario", () => {
  const mensaje = mensajeDeListaCambiada("5-api/lista-api.json", null);
  assert.match(mensaje, /Vuelve a exportar desde la misma corrida/);
  assert.doesNotMatch(mensaje, /Lo que entró está en/);
});
