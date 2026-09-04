export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

/** `docs/02-modelo-datos.md`: `RETIRO_EN_PUNTO` no lleva `Direccion`. */
export type TipoEntrega = 'ENVIO_A_DOMICILIO' | 'RETIRO_EN_PUNTO';

/** Tabla de métodos de `docs/11-pagos-y-envios.md`. */
export type MetodoPago =
  | 'TARJETA'
  | 'PSE'
  | 'NEQUI'
  | 'BANCOLOMBIA'
  | 'ADDI'
  | 'TRANSFERENCIA_MANUAL'
  | 'CONTRAENTREGA';

/** Grafo completo de `docs/02-modelo-datos.md`. Los estados de operación
 * (`EN_PREPARACION` en adelante) solo se ven en la página de seguimiento. */
export type EstadoPedido =
  | 'PAGO_PENDIENTE'
  | 'PAGADO'
  | 'PAGO_FALLIDO'
  | 'CONFIRMADO_CONTRAENTREGA'
  | 'EN_PREPARACION'
  | 'DESPACHADO'
  | 'ENTREGADO'
  | 'RECHAZADO_EN_ENTREGA'
  | 'DEVUELTO'
  | 'RECAUDO_PENDIENTE'
  | 'RECAUDO_CONCILIADO';

export interface Direccion {
  readonly codigoDaneDepartamento: string;
  readonly departamento: string;
  readonly codigoDaneCiudad: string;
  readonly ciudad: string;
  readonly direccion: string;
  readonly indicaciones: string | null;
}

export interface LineaPedido {
  readonly id: string;
  readonly varianteId: string;
  readonly sku: string;
  readonly nombre: string;
  readonly cantidad: number;
  readonly precioUnitario: Dinero;
  readonly tasaIva: number;
  readonly imagenUrl: string | null;
}

/** Solo llega cuando `metodoPago === 'TRANSFERENCIA_MANUAL'`. `referencia` es el
 * número legible del pedido, no una referencia aparte (`docs/11-pagos-y-envios.md`). */
export interface DatosTransferencia {
  readonly banco: string;
  readonly tipoCuenta: string;
  readonly numeroCuenta: string;
  readonly titular: string;
  readonly referencia: string;
}

export interface Pedido {
  readonly id: string;
  readonly numeroPedido: string;
  readonly usuarioId: string | null;
  readonly correo: string;
  readonly lineas: readonly LineaPedido[];
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
  readonly metodoPago: MetodoPago;
  readonly estado: EstadoPedido;
  readonly total: Dinero;
  readonly creadoEn: string;
  readonly datosTransferencia: DatosTransferencia | null;
}
