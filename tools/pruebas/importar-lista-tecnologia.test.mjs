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
  assert.deepEqual(porProponerDe({ fechaLista: "2026-10-10" }, resultado), {
    fechaLista: "2026-10-10",
    modelos: ["xiaomi-poco-f9-ultra-5g", "apple-airpods-pro-3"],
    configuraciones: ["samsung-galaxy-a17-5g-8gb-ram-256gb-dual-sim"],
  });
  assert.match(
    informeDeImportacion(resultado),
    /Esperan borrador: 2 modelos[\s\S]*y 1 configuraciones/,
  );
});
