/**
 * Los cinco estados de envio en los que el paquete se queda quieto. No son todos los que existen:
 * son los que el backend devuelve en esta bandeja, y la pantalla no vuelve a decidir cuales son.
 */
export type EstadoEnvioEnRevision =
  'EXCEPCION' | 'RETENIDO' | 'CANCELADO' | 'DESTRUIDO' | 'FALLIDO';

/** Los dos en los que hay plata comprometida sin desenlace. */
export type EstadoEmisionEnRevision = 'INDETERMINADA' | 'PARCIAL';

/**
 * Una guia que dejo de moverse.
 *
 * `recibidoEn` no es decorativo: es el instante contra el que el servidor compara el acuse, y lo
 * que permite distinguir un evento de hace dos meses que nadie atendio de uno que acaba de llegar.
 *
 * `revisadaEn` viene con valor solo cuando alguien ya habia mirado esta guia y despues le llego un
 * evento nuevo. Que siga en la bandeja teniendo acuse significa que hay algo posterior a lo que se
 * reviso.
 */
export interface GuiaEnRevision {
  readonly guiaId: string;
  readonly numeroGuia: string;
  readonly transportadora: string;
  readonly pedidoId: string;
  readonly numeroPedido: string;
  readonly estado: EstadoEnvioEnRevision;
  readonly descripcion: string | null;
  readonly ocurrioEn: string;
  readonly recibidoEn: string;
  readonly revisadaEn: string | null;
}

/**
 * Una emision con saldo comprometido que nadie ha desenredado.
 *
 * `idTarifa` es lo unico con lo que se puede hacer algo: es la llave con la que el panel de la
 * plataforma encuentra el envio, y la que lo recupera por idempotencia dentro de las 96 horas.
 */
export interface EmisionEnRevision {
  readonly emisionId: string;
  readonly pedidoId: string;
  readonly numeroPedido: string;
  readonly transportadora: string;
  readonly idTarifa: string;
  readonly estado: EstadoEmisionEnRevision;
  readonly detalle: string | null;
  readonly enviosEnPlataforma: readonly string[];
  readonly solicitadaEn: string;
  readonly actor: string;
}

export interface BandejaDeRevision {
  readonly guias: readonly GuiaEnRevision[];
  readonly emisiones: readonly EmisionEnRevision[];
}

export interface AcuseDeRevision {
  readonly id: string;
  readonly tipo: 'GUIA' | 'EMISION';
  readonly referencia: string;
  readonly revisadoEn: string;
  readonly actor: string;
  readonly nota: string | null;
}

/**
 * Lo que la persona vio en el panel de la plataforma. No es una opinion: es lo que habia.
 *
 * Son dos y no tres porque "no se" no es un veredicto — es dejar la emision como esta, que es lo
 * que ya pasa si nadie hace nada. Para eso esta el acuse.
 */
export type VeredictoDeEmision = 'SIN_COBRO' | 'CON_ENVIO';

/**
 * Como quedo una emision recien resuelta. Los dos veredictos llevan a sitios distintos —`FALLIDA`
 * libera el pedido, `EN_CURSO` deja a la tarea releyendo— y la pantalla tiene que poder decir cual
 * de los dos paso.
 */
export interface EmisionResuelta {
  readonly emisionId: string;
  readonly estado: string;
  readonly detalle: string | null;
  readonly enviosEnPlataforma: readonly string[];
  readonly resueltaEn: string | null;
}
