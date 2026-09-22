export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

export type TipoEntrega = 'ENVIO_A_DOMICILIO' | 'RETIRO_EN_PUNTO';

export type MetodoPago =
  | 'TARJETA'
  | 'PSE'
  | 'NEQUI'
  | 'BANCOLOMBIA'
  | 'SISTECREDITO'
  | 'TRANSFERENCIA_MANUAL'
  | 'CONTRAENTREGA';

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
  | 'RECAUDO_CONCILIADO'
  | 'CANCELADO';

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

/** Una guía del despacho: un paquete, una transportadora y lo que ese paquete nos cuesta.
 *
 * `urlEtiqueta` es el rótulo que hay que imprimir, y viene vacío más a menudo de lo que parece: una
 * guía tecleada a mano se imprimió por fuera, y de las emitidas por nosotros tampoco está
 * garantizado. La pantalla tiene que saber vivir sin él. */
export interface GuiaAdmin {
  readonly transportadora: string;
  readonly guia: string;
  readonly costo: Dinero;
  readonly urlEtiqueta: string | null;
}

/** En qué va el intento de que la transportadora emita las guías de un pedido (`adr/0033`). */
export type EstadoEmision =
  'SOLICITADA' | 'EN_CURSO' | 'INDETERMINADA' | 'EMITIDA' | 'FALLIDA' | 'PARCIAL';

/** Lo que el panel sabe de una emisión recién pedida.
 *
 * No trae guías porque todavía no las hay: la plataforma cobra al crear y el número aparece minutos
 * después. Lo que sí dice es con qué transportadora salió y cuántos paquetes son, que es lo que
 * explica por qué un pedido de dos variantes va a traer dos guías y dos cobros.
 *
 * No trae `detalle` a propósito. El servidor lo guarda —es lo que lee quien tiene que ir a buscar
 * una guía pagada—, pero está escrito en español dentro de la capa de aplicación y sin pasar por
 * Transloco: cablearlo hasta aquí es dejar preparado que alguien lo pinte y publique texto de
 * interfaz que nunca se tradujo. El día que haga falta mostrarlo, va con su llave. */
export interface EmisionDeGuiaAdmin {
  readonly id: string;
  readonly estado: EstadoEmision;
  readonly transportadora: string;
  readonly cuantosEnvios: number;
}

/** Solo existe una vez despachado el pedido (`docs/02-modelo-datos.md`).
 * `comisionRecaudo`/`recaudoConciliadoEn` quedan en `null` hasta conciliar el recaudo.
 *
 * `guias` va en plural desde `adr/0031`: ninguna transportadora colombiana admite multipaquete, así
 * que un pedido de dos variantes sale en dos guías. `costoEnvio` es la suma de todas. */
export interface EnvioAdmin {
  readonly guias: readonly GuiaAdmin[];
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

/**
 * El veredicto del plazo de entrega. **Nunca `INDETERMINADO`**, a diferencia del del retracto: los
 * treinta días del artículo 18 de la Ley 1480 de 2011 son calendario, así que no hay festivos que
 * puedan empujar el límite ni incertidumbre que declarar.
 */
export type VerdictoPlazoEntrega = 'EN_PLAZO' | 'VENCIDO';

/**
 * El plazo legal para entregar, calculado en el servidor: un plazo legal no puede depender del
 * reloj ni de la zona horaria del navegador de quien mire la pantalla.
 *
 * `avisadoEn` es cuándo se le escribió al comprador para decirle que puede terminar el contrato.
 * Vencido y sin aviso no es un estado imposible: el vigilante pasa cada doce horas.
 */
export interface PlazoDeEntregaAdmin {
  readonly inicio: string;
  readonly limite: string;
  readonly verdicto: VerdictoPlazoEntrega;
  readonly avisadoEn: string | null;
}

/** Quien recibe: para la guía y para llamar. `null` en los pedidos anteriores a que se pidiera. */
export interface ContactoAdmin {
  readonly nombre: string;
  readonly telefono: string;
}

export interface PedidoAdmin {
  readonly id: string;
  readonly numeroPedido: string;
  readonly usuarioId: string | null;
  readonly correo: string;
  readonly contacto: ContactoAdmin | null;
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
  /** Nulo mientras el plazo no haya arrancado: un pago pendiente no tiene contrato que incumplir. */
  readonly plazoDeEntrega: PlazoDeEntregaAdmin | null;
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

/**
 * Por donde entro el dinero que la transportadora recaudo en la puerta: creditos de la plataforma
 * (sin comision, inmediato) o consignacion bancaria (con comision, los jueves).
 *
 * No es una instruccion que el sitio le de a nadie: la eleccion se hace en el panel de la plataforma
 * al retirar el saldo, y aqui se registra lo que ya paso. Ver `adr/0043`.
 */
export type ModalidadRecaudo = 'CREDITOS' | 'BANCO';

export const MODALIDADES_RECAUDO: readonly ModalidadRecaudo[] = ['CREDITOS', 'BANCO'];

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
