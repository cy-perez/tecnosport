/**
 * Tres valores y no dos. `INDETERMINADA` significa que el termino de esa categoria no estaba
 * cargado el dia que se radico —hoy, el de los celulares, mientras el plazo del fabricante siga
 * pendiente— y sin el no se puede afirmar que la garantia vencio. La pantalla tiene que decirlo con
 * esas palabras, no pintarlo como vencida.
 */
export type VigenciaGarantia = 'CUBIERTA' | 'FUERA_DE_TERMINO' | 'INDETERMINADA';

/** Las tres salidas de la ley. Devolver el dinero es una de ellas, no la unica. */
export type DesenlaceGarantia = 'REPARACION' | 'REPOSICION' | 'REINTEGRO';

export type EstadoReclamacionGarantia = 'RADICADA' | 'RESUELTA';

export interface ReclamacionGarantia {
  readonly id: string;
  /** La solicitud de atencion que la contiene, con su radicado y su plazo de respuesta. */
  readonly solicitudId: string;
  readonly pedidoId: string;
  readonly varianteId: string;
  readonly entregadoEn: string;
  readonly radicadaEn: string;
  readonly mesesDeTermino: number | null;
  /** Nulo cuando el termino no se conoce; no una fecha inventada. */
  readonly finDelTermino: string | null;
  readonly vigencia: VigenciaGarantia;
  readonly descripcionDelFallo: string;
  readonly estado: EstadoReclamacionGarantia;
  readonly desenlace: DesenlaceGarantia | null;
  readonly resueltaEn: string | null;
  readonly resueltaPor: string | null;
  readonly reintegroId: string | null;
}

export const DESENLACES: readonly DesenlaceGarantia[] = ['REPARACION', 'REPOSICION', 'REINTEGRO'];

/** La garantia se cuenta desde la entrega, asi que solo un pedido entregado admite reclamarla. */
export const ESTADOS_QUE_ADMITEN_GARANTIA = [
  'ENTREGADO',
  'RECAUDO_PENDIENTE',
  'RECAUDO_CONCILIADO',
  'DEVUELTO',
] as const;
