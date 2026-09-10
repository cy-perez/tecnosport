export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

export type TipoEntrega = 'ENVIO_A_DOMICILIO' | 'RETIRO_EN_PUNTO';

export type MetodoPago = 'TARJETA' | 'PSE' | 'NEQUI' | 'BANCOLOMBIA' | 'ADDI' | 'TRANSFERENCIA_MANUAL' | 'CONTRAENTREGA';

/** Grafo completo de `docs/02-modelo-datos.md` — el panel opera todos los estados, a
 * diferencia de la página de seguimiento del cliente. */
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

export interface LineaPedidoAdmin {
  readonly id: string;
  readonly varianteId: string;
  readonly sku: string;
  readonly nombre: string;
  readonly cantidad: number;
  readonly precioUnitario: Dinero;
  readonly tasaIva: number;
  readonly imagenUrl: string | null;
}

/** Solo llega cuando `metodoPago === 'TRANSFERENCIA_MANUAL'`. */
export interface DatosTransferencia {
  readonly banco: string;
  readonly tipoCuenta: string;
  readonly numeroCuenta: string;
  readonly titular: string;
  readonly referencia: string;
}

/** Solo existe una vez despachado el pedido (`docs/02-modelo-datos.md`).
 * `comisionRecaudo`/`recaudoConciliadoEn` quedan en `null` hasta conciliar el recaudo. */
export interface EnvioAdmin {
  readonly transportadora: string;
  readonly guia: string;
  readonly costoEnvio: Dinero;
  readonly despachadoEn: string;
  readonly comisionRecaudo: Dinero | null;
  readonly recaudoConciliadoEn: string | null;
}

export interface HistorialPedidoAdmin {
  readonly estado: EstadoPedido;
  readonly fecha: string;
  readonly actor: string;
  readonly motivo: string;
}

export interface PedidoAdmin {
  readonly id: string;
  readonly numeroPedido: string;
  readonly usuarioId: string | null;
  readonly correo: string;
  readonly lineas: readonly LineaPedidoAdmin[];
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
  readonly metodoPago: MetodoPago;
  readonly estado: EstadoPedido;
  readonly total: Dinero;
  /**
   * Cuánto entró de verdad por el pedido, que no es `total`: aquél es lo que el comprador debe. En
   * contraentrega el dinero es del negocio cuando el recaudo se concilia, no cuando se entrega.
   */
  readonly dineroRecibido: Dinero;
  /** Cuánto ya volvió al comprador, contando los cinco motivos y lo que revirtió el emisor. */
  readonly yaDevuelto: Dinero;
  readonly creadoEn: string;
  readonly datosTransferencia: DatosTransferencia | null;
  readonly envio: EnvioAdmin | null;
  readonly historial: readonly HistorialPedidoAdmin[];
}

export interface PedidosPaginadosAdmin {
  readonly items: readonly PedidoAdmin[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalPedidos: number;
}

export interface FiltroPedidosAdmin {
  readonly pagina: number;
  readonly tamano: number;
  readonly estado: EstadoPedido | null;
}

/**
 * Por que el negocio cancela un pedido antes de despacharlo. Los dos salen de los terminos
 * publicados: "Disponibilidad" y "Envio y entrega".
 */
export type MotivoCancelacion = 'NO_DISPONIBILIDAD' | 'PLAZO_INCUMPLIDO';

export const MOTIVOS_CANCELACION: readonly MotivoCancelacion[] = [
  'NO_DISPONIBILIDAD',
  'PLAZO_INCUMPLIDO',
];

/** Solo antes de despachar: despues ya existen entrega, rechazo en la entrega y devolucion. */
export const ESTADOS_QUE_ADMITEN_CANCELACION = [
  'PAGO_PENDIENTE',
  'PAGADO',
  'CONFIRMADO_CONTRAENTREGA',
  'EN_PREPARACION',
] as const;
