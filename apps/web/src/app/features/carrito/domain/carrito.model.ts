export interface LineaCarrito {
  readonly id: string;
  readonly varianteId: string;
  readonly cantidad: number;
  /**
   * El enlace de compra con que entró, si el color de la variante se confirmó con el proveedor
   * (ADR-0086). El checkout lo devuelve al crear el pedido; el servidor decide si sigue vigente.
   */
  readonly enlaceCompra?: string | null;
}

export interface Carrito {
  readonly id: string;
  readonly usuarioId: string | null;
  readonly lineas: readonly LineaCarrito[];
  readonly creadoEn: string;
}

/**
 * El carrito con los precios de hoy, del servidor. Es la única fuente de precio y de subtotal que
 * se le muestra al comprador: la foto guardada al agregar (`SnapshotLinea`) sirve para el nombre y
 * la imagen, no para cobrar ni para informar cuánto se cobra.
 */
export interface CarritoCotizado {
  readonly lineas: readonly LineaCotizada[];
  /** Al precio publicado: lo que se paga con pasarela, y el único que se anuncia como precio. */
  readonly subtotal: number;
  /**
   * Al precio de contado: lo que se paga por transferencia, en el punto o contra entrega
   * (ADR-0095). No se pinta como un segundo total: la diferencia se dice como ahorro.
   */
  readonly subtotalContado: number;
}

export interface LineaCotizada {
  readonly lineaId: string;
  readonly varianteId: string;
  readonly cantidad: number;
  /** `null` si la variante ya no se vende. */
  readonly precioUnitario: number | null;
  readonly subtotal: number | null;
  /** Al precio de contado (ADR-0095); `null` si la variante ya no se vende. */
  readonly precioContado: number | null;
  readonly subtotalContado: number | null;
}
