// Las tomas de un modelo salen de su carpeta en `catalogo/entregables/fichas/` (decisión del
// 08/10/2026): la maestra de «Fotos procesadas» para juzgar, y su AVIF de `web/<ancho>/` para
// subir. Hasta esa fecha se leían de `catalogo/fotos/estudio/<id>/`, que ya no existe.
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";
import { test } from "node:test";
import assert from "node:assert/strict";
import { contadoSugeridoDe, fotosDeCarpeta } from "../material-catalogo.mjs";

/** Lo mínimo que `dimensionesJpeg` sabe leer: el inicio y un marco SOF0 con alto y ancho. */
function jpeg(ancho, alto) {
  const b = Buffer.alloc(13);
  b.set([0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08]);
  b.writeUInt16BE(alto, 7);
  b.writeUInt16BE(ancho, 9);
  return b;
}

function carpetaDePrueba() {
  const raiz = mkdtempSync(join(tmpdir(), "fichas-"));
  const procesadas = join(raiz, "Samsung Galaxy A57 5G", "Fotos procesadas");
  mkdirSync(join(procesadas, "web", "1200"), { recursive: true });
  mkdirSync(join(procesadas, "web", "800"), { recursive: true });
  writeFileSync(join(procesadas, "samsung-galaxy-a57-5g_1.jpg"), jpeg(2000, 2000));
  writeFileSync(join(procesadas, "web", "1200", "samsung-galaxy-a57-5g_1.avif"), "avif");
  writeFileSync(join(procesadas, "web", "800", "samsung-galaxy-a57-5g_1.avif"), "avif");
  writeFileSync(join(procesadas, "web", "1200", "samsung-galaxy-a57-5g_1.jpg"), jpeg(1200, 1200));
  return { raiz, modelo: join(raiz, "Samsung Galaxy A57 5G") };
}

test("las tomas salen de «Fotos procesadas», con su maestra y sus anchos web", () => {
  const { raiz, modelo } = carpetaDePrueba();
  try {
    const { archivos, ladoMenor } = fotosDeCarpeta(modelo);
    assert.equal(ladoMenor, 2000);
    assert.equal(archivos.length, 1);
    assert.deepEqual(
      archivos[0].variantes.map((v) => v.ancho),
      [1200, 800],
    );
    assert.equal(archivos[0].web.ancho, 1200);
    assert.equal(archivos[0].vistaPrevia.contentType, "image/jpeg");
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
});

test("un carrusel menor que 1200 se encuentra, y lo que pasa del tope no se sube", () => {
  // Desde el 16/10/2026 el estudio deja el carrusel y la miniatura; un producto que no llega a
  // 1200 publica su carrusel en el ancho de su lienzo. Con una lista fija de anchos esa carpeta no
  // la veía nadie y la ficha se servía con la miniatura de 480.
  const raiz = mkdtempSync(join(tmpdir(), "fichas-"));
  try {
    const modelo = join(raiz, "JBL Xtreme 4");
    const procesadas = join(modelo, "Fotos procesadas");
    for (const ancho of ["1000", "480", "2000"]) {
      mkdirSync(join(procesadas, "web", ancho), { recursive: true });
      writeFileSync(join(procesadas, "web", ancho, "jbl-xtreme-4_1.avif"), "avif");
    }
    mkdirSync(join(procesadas, "web", "notas"), { recursive: true });
    writeFileSync(join(procesadas, "jbl-xtreme-4_1.jpg"), jpeg(1000, 1000));
    writeFileSync(join(procesadas, "web", "1000", "jbl-xtreme-4_1.jpg"), jpeg(1000, 1000));
    const [toma] = fotosDeCarpeta(modelo).archivos;
    assert.deepEqual(
      toma.variantes.map((v) => v.ancho),
      [1000, 480],
    );
    assert.equal(toma.web.ancho, 1000);
    assert.equal(toma.vistaPrevia.ancho, 1000);
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
});

test("un modelo sin carpeta o sin fotos procesadas no tiene tomas", () => {
  assert.deepEqual(fotosDeCarpeta(null), { archivos: [], ladoMenor: 0 });
  const raiz = mkdtempSync(join(tmpdir(), "fichas-"));
  try {
    assert.deepEqual(fotosDeCarpeta(join(raiz, "Sin fotos")), { archivos: [], ladoMenor: 0 });
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
});

test("las tomas van en orden natural: la _10 después de la _2", () => {
  const { raiz, modelo } = carpetaDePrueba();
  try {
    const procesadas = join(modelo, "Fotos procesadas");
    writeFileSync(join(procesadas, "samsung-galaxy-a57-5g_10.jpg"), jpeg(2000, 2000));
    writeFileSync(join(procesadas, "samsung-galaxy-a57-5g_2.jpg"), jpeg(2000, 2000));
    const nombres = fotosDeCarpeta(modelo).archivos.map((f) => basename(f.ruta));
    assert.deepEqual(nombres, [
      "samsung-galaxy-a57-5g_1.jpg",
      "samsung-galaxy-a57-5g_2.jpg",
      "samsung-galaxy-a57-5g_10.jpg",
    ]);
  } finally {
    rmSync(raiz, { recursive: true, force: true });
  }
});

// El contado que carga el cargador es la sugerencia del panel (ADR-0095, decisión 8).
test("el contado es el de mercado cuando no le gana al costo más de 400.000", () => {
  assert.equal(
    contadoSugeridoDe({ precio_proveedor_cop: 675_000, precio_mercado_cop: 849_900 }),
    849_900,
  );
});

test("cuando manda el tope, el contado baja a la unidad de mil", () => {
  // 675.500 + 400.000 = 1.075.500: subir a 1.076.000 rompería el tope.
  assert.equal(
    contadoSugeridoDe({ precio_proveedor_cop: 675_500, precio_mercado_cop: 1_200_000 }),
    1_075_000,
  );
});

test("sin costo en la lista, el contado es el de mercado", () => {
  assert.equal(contadoSugeridoDe({ precio_mercado_cop: 1_200_000 }), 1_200_000);
});

test("sin precio de mercado no hay contado que sugerir", () => {
  assert.equal(contadoSugeridoDe({ precio_proveedor_cop: 675_000 }), null);
});
