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
