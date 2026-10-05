export interface LineaCarrito {
  readonly id: string;
  readonly varianteId: string;
  readonly cantidad: number;
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
  readonly subtotal: number;
}

export interface LineaCotizada {
  readonly lineaId: string;
  readonly varianteId: string;
  readonly cantidad: number;
  /** `null` si la variante ya no se vende. */
  readonly precioUnitario: number | null;
  readonly subtotal: number | null;
}
