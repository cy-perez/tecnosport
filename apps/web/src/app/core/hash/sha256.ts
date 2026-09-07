/**
 * El SHA-256 de un archivo, en hexadecimal — lo que el backend guarda como `hash` de una imagen
 * (docs/02-modelo-datos.md).
 *
 * Lo calcula el navegador porque es el único que tiene los bytes: en una subida directa a Cloud
 * Storage el archivo nunca pasa por el servidor. El backend no puede verificar que el hash
 * corresponda al archivo sin descargarlo (ADR-0016); lo que sí exige es que sea un SHA-256 bien
 * formado.
 *
 * `crypto.subtle` solo existe en un contexto seguro (HTTPS o localhost) — el mismo requisito que ya
 * imponen la cámara y el sensor de orientación del asistente de captura.
 */
export async function sha256Hex(archivo: Blob): Promise<string> {
  const resumen = await crypto.subtle.digest('SHA-256', await archivo.arrayBuffer());
  return Array.from(new Uint8Array(resumen))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}
