import { VerdictoPlazo } from '../../retractos/domain/retracto.model';

export type { VerdictoPlazo };

/**
 * Cuatro y no mas: son tasadas (Ley 1480 de 2011, art. 51). Cual se invoco decide a quien le toca
 * responder y que prueba hace falta, asi que no es una etiqueta de reporte.
 */
export type CausalReversion =
  | 'FRAUDE'
  | 'PRODUCTO_NO_ENTREGADO'
  | 'PRODUCTO_NO_CORRESPONDE'
  | 'PRODUCTO_DEFECTUOSO';

/**
 * Los dos primeros estan separados porque el dinero vuelve por caminos distintos y solo uno deja
 * constancia aqui: si revierte el emisor, la plata regresa por la red de pagos y el comercio no
 * movio un peso.
 */
export type DesenlaceReversion =
  | 'REVERTIDO_POR_EL_EMISOR'
  | 'REINTEGRADO_DIRECTAMENTE'
  | 'RECHAZADA'
  | 'DESISTIDA';

export type EstadoSolicitudReversion = 'RADICADA' | 'GESTIONADA' | 'RESUELTA';

export interface SolicitudReversion {
  readonly id: string;
  readonly solicitudId: string;
  readonly pedidoId: string;
  readonly causal: CausalReversion;
  /** Cuando el comprador tuvo noticia del hecho: de aqui cuelga su plazo de cinco dias habiles. */
  readonly fechaDeNoticia: string;
  readonly radicadaEn: string;
  readonly verdictoAlRadicar: VerdictoPlazo;
  readonly estado: EstadoSolicitudReversion;
  readonly gestionadaEn: string | null;
  readonly gestionadaPor: string | null;
  /** Lo que demuestra que se facilito el tramite, que es lo que los terminos prometen. */
  readonly gestion: string | null;
  readonly desenlace: DesenlaceReversion | null;
  readonly resueltaEn: string | null;
  readonly reintegroId: string | null;
}

export const CAUSALES: readonly CausalReversion[] = [
  'FRAUDE',
  'PRODUCTO_NO_ENTREGADO',
  'PRODUCTO_NO_CORRESPONDE',
  'PRODUCTO_DEFECTUOSO',
];

export const DESENLACES_REVERSION: readonly DesenlaceReversion[] = [
  'REVERTIDO_POR_EL_EMISOR',
  'REINTEGRADO_DIRECTAMENTE',
  'RECHAZADA',
  'DESISTIDA',
];
