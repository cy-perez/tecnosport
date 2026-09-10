/**
 * Tres valores y no dos, igual que en el retracto. `INDETERMINADO` no es un error: paso el limite
 * mas temprano posible pero el calendario de festivos no esta cargado, y sin el, afirmar que un
 * plazo vencio seria acusar de un incumplimiento que quiza no ocurrio. La pantalla tiene que
 * decirlo con esas palabras.
 */
export type VerdictoPlazo = 'EN_PLAZO' | 'VENCIDO' | 'INDETERMINADO';

/**
 * De el depende que reloj corre. El mismo buzon recibe solicitudes con plazos legales distintos, y
 * por eso el tipo se elige al radicar y no se puede cambiar despues.
 */
export type TipoSolicitud =
  | 'PETICION'
  | 'QUEJA'
  | 'RECLAMO'
  | 'CONSULTA_DATOS'
  | 'RECLAMO_DATOS'
  | 'GARANTIA'
  | 'REVERSION';

export type EstadoSolicitudAtencion = 'RADICADA' | 'PRORROGADA' | 'RESPONDIDA';

export interface Prorroga {
  readonly otorgadaEn: string;
  readonly otorgadaPor: string;
  readonly motivo: string;
  /** Lo que la hace valida: la ley concede los dias extra por avisar, no por otorgarla. */
  readonly avisadaEn: string;
}

export interface RespuestaSolicitud {
  readonly respondidaEn: string;
  readonly respondidaPor: string;
  readonly resumen: string;
}

export interface SolicitudAtencion {
  readonly id: string;
  /** El numero que el interesado puede citar para preguntar por lo suyo. */
  readonly numeroRadicado: string;
  readonly tipo: TipoSolicitud;
  readonly correo: string;
  readonly pedidoId: string | null;
  /** De esta cuelga el plazo. */
  readonly recibidaEn: string;
  /** Cuando alguien la registro; la distancia con la anterior explica los retrasos. */
  readonly radicadaEn: string;
  readonly radicadaPor: string;
  readonly asunto: string;
  readonly estado: EstadoSolicitudAtencion;
  /** Lo calcula el servidor: es un plazo legal y no puede depender del reloj del navegador. */
  readonly limiteDeRespuesta: string;
  readonly verdicto: VerdictoPlazo;
  readonly prorroga: Prorroga | null;
  readonly respuesta: RespuestaSolicitud | null;
}

/** Los unicos que admiten prorroga: los de datos personales. El resto no la tiene en el texto. */
export const TIPOS_CON_PRORROGA: readonly TipoSolicitud[] = ['CONSULTA_DATOS', 'RECLAMO_DATOS'];

export const TIPOS_DE_SOLICITUD: readonly TipoSolicitud[] = [
  'PETICION',
  'QUEJA',
  'RECLAMO',
  'CONSULTA_DATOS',
  'RECLAMO_DATOS',
  'GARANTIA',
  'REVERSION',
];
