/**
 * El enlace de compra que llega por WhatsApp cuando el proveedor confirma un color de tecnología
 * (ADR-0086): qué variante deja comprar y hasta cuándo. Mientras esté vigente, la unidad está
 * apartada para quien lo recibió.
 */
export interface EnlaceDeCompra {
  readonly token: string;
  readonly varianteId: string;
  /** ISO-8601, en UTC, como lo manda la API. */
  readonly expiraEn: string;
}
