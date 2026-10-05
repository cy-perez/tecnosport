/** Lo único que hace falta de un `<canvas>` para codificarlo. Así se prueba sin canvas de verdad. */
export interface LienzoCodificable {
  toBlob(llamada: (blob: Blob | null) => void, tipo?: string, calidad?: number): void;
}

function aBlob(lienzo: LienzoCodificable, tipo: string, calidad: number): Promise<Blob | null> {
  return new Promise((resolver) => lienzo.toBlob(resolver, tipo, calidad));
}

/**
 * WebP, y si el navegador no lo codifica, JPEG con la misma calidad.
 *
 * <p>`toBlob` con un tipo no soportado no falla: entrega PNG (especificación de HTML). Así que no
 * basta con mirar si devolvió algo, hay que mirar **qué** devolvió. PNG no es un respaldo
 * aceptable: un fotograma de 1000 px pesa varias veces lo que su JPEG, y el visor descarga el set
 * entero. Ver `domain/formato-de-fotograma.ts`.
 *
 * <p>Devuelve `null` si ninguno de los dos produjo imagen; quien llama decide el error.
 */
export async function codificarFotograma(
  lienzo: LienzoCodificable,
  calidad: number,
): Promise<Blob | null> {
  const webp = await aBlob(lienzo, 'image/webp', calidad);
  if (webp !== null && webp.type === 'image/webp') {
    return webp;
  }
  const jpeg = await aBlob(lienzo, 'image/jpeg', calidad);
  return jpeg !== null && jpeg.type === 'image/jpeg' ? jpeg : null;
}
