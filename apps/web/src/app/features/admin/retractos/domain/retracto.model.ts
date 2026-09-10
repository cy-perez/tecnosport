/**
 * Tres valores y no dos, igual que en el backend. `INDETERMINADO` no es un error: significa que
 * pasó el límite más temprano posible pero el calendario de festivos no está cargado, y sin él
 * afirmar que un plazo venció sería negarle un derecho a alguien que quizá está a tiempo. La
 * pantalla tiene que decirlo con esas palabras, no pintarlo como vencido.
 */
export type VerdictoPlazo = 'EN_PLAZO' | 'VENCIDO' | 'INDETERMINADO';

export type EstadoSolicitudRetracto =
  | 'RADICADA'
  | 'PRODUCTO_RECIBIDO'
  | 'REEMBOLSADA'
  | 'RECHAZADA';

export type MedioReintegro = 'WOMPI' | 'TRANSFERENCIA_BANCARIA' | 'EFECTIVO' | 'OTRO';

export interface Reintegro {
  readonly monto: number;
  readonly medio: MedioReintegro;
  readonly comprobante: string | null;
  readonly registradoEn: string;
  readonly registradoPor: string;
}

export interface SolicitudRetracto {
  readonly id: string;
  readonly pedidoId: string;
  readonly radicadaEn: string;
  readonly radicadaPor: string;
  readonly motivo: string | null;
  readonly verdictoAlRadicar: VerdictoPlazo;
  readonly estado: EstadoSolicitudRetracto;
  readonly productoRecibidoEn: string | null;
  /** Lo calcula el servidor: es el plazo del artículo 47 y no puede depender del reloj del navegador. */
  readonly limiteDeReintegro: string | null;
  /**
   * Por dónde pidió el comprador que le devolvieran el dinero (Ley 2439 de 2024). `null` cuando no
   * lo dijo, que es distinto de "da igual": sin preferencia no hay nada que incumplir, con
   * preferencia devolver por otro medio es un incumplimiento.
   */
  readonly medioPreferido: MedioReintegro | null;
  /**
   * Si el reintegro respetó esa preferencia. **Lo decide el servidor**, no esta pantalla: es la
   * respuesta a "cumplimos o no", y no se deja a una comparación de cadenas en el navegador.
   * `null` mientras no haya reintegro — todavía no hay nada que comparar.
   */
  readonly preferenciaRespetada: boolean | null;
  readonly reintegro: Reintegro | null;
}

/** Solo un pedido entregado admite retracto, y el grafo llega a `DEVUELTO` por los dos caminos. */
export const ESTADOS_QUE_ADMITEN_RETRACTO = [
  'ENTREGADO',
  'RECAUDO_PENDIENTE',
  'RECAUDO_CONCILIADO',
] as const;
