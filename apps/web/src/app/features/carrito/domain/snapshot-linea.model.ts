/**
 * Foto del producto al momento de agregarlo al carrito — no autoritativa, solo para pintar la
 * página (`GET /carritos/{id}` solo trae varianteId y cantidad, docs/03-api.md). Sin importar tipos
 * de `catalogo`: mantiene los dos features desacoplados.
 */
export interface SnapshotLinea {
  readonly varianteId: string;
  readonly nombreProducto: string;
  readonly slugProducto: string;
  readonly sku: string;
  readonly imagenUrl: string | null;
  readonly imagenAlt: string;
  readonly precioValor: number;
  readonly precioMoneda: string;
  /**
   * Lo que eligió de la variante, para que el carrito lo diga: «Negro · M». Opcional porque un
   * carrito guardado antes del 3 de octubre de 2026 no lo trae, y entonces solo se ve el SKU.
   */
  readonly detalleVariante?: string | null;
}
