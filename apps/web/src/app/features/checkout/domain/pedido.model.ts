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

export type EstadoRetracto = 'RADICADA' | 'PRODUCTO_RECIBIDO' | 'REEMBOLSADA' | 'RECHAZADA';

/**
 * El retracto tal como lo ve quien lo ejerció. No trae ni el actor que lo atendió ni el veredicto
 * de plazo: eso es información interna del panel (`RetractoPublicoRespuesta` en el backend).
 */
export interface RetractoPublico {
  readonly estado: EstadoRetracto;
  readonly radicadaEn: string;
  readonly motivo: string | null;
  readonly productoRecibidoEn: string | null;
  readonly limiteDeReintegro: string | null;
  readonly montoReembolsado: Dinero | null;
  readonly reembolsadoEn: string | null;
}

/**
 * Lo que devuelve `GET /pedidos/{id}/seguimiento`. Tipo propio y no `Pedido` con un campo más: el
 * backend separó las dos respuestas justamente porque compartirlas fue lo que dejó salir el costo
 * real del flete, y repetir aquí la mezcla desharía esa separación desde el otro lado.
 */
export interface Seguimiento extends Pedido {
  readonly retractos: readonly RetractoPublico[];
}
