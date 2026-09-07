import { describe, expect, it } from 'vitest';

import { sha256Hex } from './sha256';

describe('sha256Hex', () => {
  /** El vector conocido: SHA-256 de la cadena vacía. */
  it('calcula el hash de un archivo vacío', async () => {
    expect(await sha256Hex(new Blob([]))).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });

  /** El otro vector de siempre: "abc". */
  it('calcula el hash de un contenido conocido', async () => {
    expect(await sha256Hex(new Blob(['abc']))).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  });

  it('devuelve 64 hexadecimales en minúscula, que es lo que el backend acepta', async () => {
    const hash = await sha256Hex(new Blob([new Uint8Array([1, 2, 3, 250, 251])]));

    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('el mismo contenido da el mismo hash y otro contenido da otro', async () => {
    const uno = await sha256Hex(new Blob(['fotograma']));
    const otroIgual = await sha256Hex(new Blob(['fotograma']));
    const distinto = await sha256Hex(new Blob(['fotograma ']));

    expect(otroIgual).toBe(uno);
    expect(distinto).not.toBe(uno);
  });
});
