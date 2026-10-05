/**
 * En qué formato sube un set de fotogramas.
 *
 * <p>WebP con respaldo JPEG, que es lo que promete el contrato del visor (`docs/10-captura-360.md`,
 * "Contrato entre las dos piezas"). El respaldo no es teórico: `canvas.toBlob(…, 'image/webp')`
 * **no falla** en un navegador que no codifica WebP, entrega un PNG sin avisar —es lo que dice la
 * especificación de HTML para un tipo no soportado—, y Safari de iOS anterior al 17 es ese
 * navegador. Con el tipo fijo en `'image/webp'`, el asistente pedía la URL firmada para WebP y
 * subía un PNG, y Cloud Storage rechaza el `PUT` porque el `Content-Type` no es el que se firmó.
 *
 * <p>Por eso el tipo se decide **mirando los blobs**, no se supone. Y es uno solo por set: el
 * backend firma todas las URL del set con el mismo tipo en una sola petición.
 */
export const FORMATOS_DE_FOTOGRAMA = ['image/webp', 'image/jpeg'] as const;

export type FormatoDeFotograma = (typeof FORMATOS_DE_FOTOGRAMA)[number];

export function esFormatoDeFotograma(tipo: string): tipo is FormatoDeFotograma {
  return (FORMATOS_DE_FOTOGRAMA as readonly string[]).includes(tipo);
}

/**
 * El formato común de un set, o `null` si no lo hay: un blob en un formato que no se sube, o dos
 * formatos mezclados. Un set mezclado no se puede subir con una sola firma, y adivinar cuál de los
 * dos manda sería subir la mitad con el `Content-Type` equivocado.
 */
export function formatoComun(blobs: readonly Blob[]): FormatoDeFotograma | null {
  if (blobs.length === 0) {
    return null;
  }
  const primero = blobs[0].type;
  if (!esFormatoDeFotograma(primero)) {
    return null;
  }
  return blobs.every((blob) => blob.type === primero) ? primero : null;
}
