export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

/** `docs/02-modelo-datos.md`: `RETIRO_EN_PUNTO` no lleva `Direccion`. */
export type TipoEntrega = 'ENVIO_A_DOMICILIO' | 'RETIRO_EN_PUNTO';

/** Tabla de métodos de `docs/11-pagos-y-envios.md`. */
/**
 * Espejo de `MetodoPago` del backend.
 *
 * <p><b>`WOMPI` es uno y fueron cuatro.</b> Hasta el 28 de septiembre de 2026 había `TARJETA`,
 * `PSE`, `NEQUI` y `BANCOLOMBIA`. Los cuatro los cobra Wompi, y lo que el comprador elegía aquí
 * nunca viajaba: el Web Checkout hospedado pinta su propia lista y el comprador vuelve a elegir
 * allí, así que un pedido podía decir NEQUI y haberse cobrado con tarjeta. Agruparlos dice lo que
 * de verdad se sabe; con qué se cobró vive en `medio_reportado_pasarela`, del lado del servidor.
 */
export type MetodoPago = 'WOMPI' | 'SISTECREDITO' | 'TRANSFERENCIA_MANUAL' | 'CONTRAENTREGA';

/**
 * Los tipos de documento que acepta la pasarela de Sistecrédito (`adr/0048`). No son todos los que
 * existen en Colombia: son los que el único consumidor que hoy los pide sabe recibir.
 */
export type TipoDocumento = 'CC' | 'TI' | 'TIE' | 'NIT';

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

/**
 * A quién se le entrega y a qué número se le avisa. Distinto del correo: el correo identifica al
 * comprador, el contacto a quien recibe, y no siempre son la misma persona. Sin esto no hay guía
 * que emitir ni mensajero de contraentrega que avise.
 */
export interface Contacto {
  readonly nombre: string;
  readonly telefono: string;
}

export interface Direccion {
  readonly codigoDaneDepartamento: string;
  readonly departamento: string;
  readonly codigoDaneCiudad: string;
  readonly ciudad: string;
  readonly direccion: string;
  readonly indicaciones: string | null;
  /**
   * El `area_level3` de la plataforma de envios. Opcional: mejora la direccion que se imprime
   * en la guia y no condiciona el precio, que sale del codigo DANE.
   */
  readonly barrio: string | null;
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
  /** Lo que se eligió de la variante, «Negro · M»; nulo en los pedidos de antes. */
  readonly detalleVariante: string | null;
}

/** Una cuenta a la que transferir. `entidad` y no `banco`: dos de las tres son
 * billeteras —Nequi y Daviplata— y el comprador las busca por ese nombre en su
 * app, no por el del banco que hay detrás. */
export interface CuentaDeTransferencia {
  readonly entidad: string;
  readonly tipoCuenta: string;
  readonly numeroCuenta: string;
  readonly titular: string;
}

/** Solo llega cuando `metodoPago === 'TRANSFERENCIA_MANUAL'`. `referencia` es el
 * número legible del pedido, no una referencia aparte (`docs/11-pagos-y-envios.md`),
 * y por eso no se repite en cada cuenta: es del pedido, no de la cuenta.
 *
 * <p>Era una sola cuenta hasta el 28 de septiembre de 2026. */
export interface DatosTransferencia {
  readonly cuentas: readonly CuentaDeTransferencia[];
  readonly referencia: string;
}

export interface Pedido {
  readonly id: string;
  readonly numeroPedido: string;
  readonly usuarioId: string | null;
  readonly correo: string;
  /** `null` solo en pedidos anteriores a que se pidiera. */
  readonly contacto: Contacto | null;
  readonly lineas: readonly LineaPedido[];
  readonly tipoEntrega: TipoEntrega;
  readonly direccion: Direccion | null;
  readonly metodoPago: MetodoPago;
  readonly estado: EstadoPedido;
  /** Solo las líneas. */
  readonly subtotal: Dinero;
  /** Lo que el comprador paga de flete, ya cobrado: no es la cotización, es lo que quedó congelado. */
  readonly costoEnvio: Dinero;
  /** Líneas más envío. */
  readonly total: Dinero;
  readonly creadoEn: string;
  readonly datosTransferencia: DatosTransferencia | null;
  /** La transportadora que eligió el comprador (`ADR-0073`); `null` en recogida o en pedidos de antes. */
  readonly transportadora: string | null;
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
 * El envío tal como lo ve quien compró: por dónde va el paquete y con qué número. **No lleva el
 * costo**, y esa ausencia es la parte importante — lo que la transportadora nos cobra es el margen
 * del negocio (`EnvioPublicoRespuesta` en el backend, hallazgo 3 de `docs/12-legales-de-envio.md`).
 * Lo que el comprador pagó de flete vive en `Pedido.costoEnvio`, que es otra cifra.
 */
export interface GuiaPublica {
  readonly transportadora: string;
  readonly guia: string;
}

export interface EnvioPublico {
  /** Varias cuando el pedido salió en varios paquetes (`adr/0031`): van por separado y pueden
   * llegar en días distintos, así que quien compró tiene que verlas todas. */
  readonly guias: readonly GuiaPublica[];
  readonly despachadoEn: string;
}

/**
 * Lo que devuelve `GET /pedidos/{id}/seguimiento`. Tipo propio y no `Pedido` con un campo más: el
 * backend separó las dos respuestas justamente porque compartirlas fue lo que dejó salir el costo
 * real del flete, y repetir aquí la mezcla desharía esa separación desde el otro lado.
 */
export interface Seguimiento extends Pedido {
  /** `null` mientras el pedido no se haya despachado. */
  readonly envio: EnvioPublico | null;
  readonly retractos: readonly RetractoPublico[];
}
