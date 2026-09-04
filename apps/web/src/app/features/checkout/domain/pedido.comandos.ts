import { Direccion, MetodoPago, TipoEntrega } from './pedido.model';

/** Solo lleva lo que el comprador elige. El precio, el SKU, el nombre y la
 * imagen los decide el servidor con el catálogo real (`docs/00-producto.md`) —
 * el mismo criterio que ya sigue `AgregarLineaRequest` del carrito. */
export interface LineaComando {
  readonly varianteId: string;
  readonly cantidad: number;
}

export interface CrearPedidoComando {
  readonly correo: string;
  readonly lineas: readonly LineaComando[];
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
  readonly metodoPago: MetodoPago;
}

/** Mismos criterios que `CrearPedidoComando` menos el método de pago: es
 * justamente lo que hace falta para decidir cuáles métodos aplican. */
export interface MetodosDePagoDisponiblesComando {
  readonly correo: string;
  readonly lineas: readonly LineaComando[];
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
}

/**
 * Lo que la página de dirección/resumen recoge, sin las líneas: esas se leen
 * siempre en vivo desde `CarritoStore` en el momento de consultar métodos de
 * pago o crear el pedido, nunca de una copia guardada aquí que podría quedar
 * desactualizada si el comprador vuelve al carrito a cambiar algo.
 */
export interface DatosEntrega {
  readonly correo: string;
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
}
