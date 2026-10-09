// Las tomas de un modelo salen de su carpeta en `catalogo/entregables/fichas/` (decisión del
// 08/10/2026): la maestra de «Fotos procesadas» para juzgar, y su AVIF de `web/<ancho>/` para
// subir. Hasta esa fecha se leían de `catalogo/fotos/estudio/<id>/`, que ya no existe.
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";
import { test } from "node:test";
import assert from "node:assert/strict";
import { fotosDeCarpeta } from "../material-catalogo.mjs";

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
    assert.deepEqual(archivos[0].variantes.map((v) => v.ancho), [1200, 800]);
    assert.equal(archivos[0].web.ancho, 1200);
    assert.equal(archivos[0].vistaPrevia.contentType, "image/jpeg");
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
