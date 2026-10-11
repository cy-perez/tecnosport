/**
 * El ciclo de un lote: se recibe la exportación, se procesa en segundo plano y termina bien o con
 * error. Mientras se procesa se puede pausar —el servidor lo suelta y la cola sigue con los de otros
 * proveedores; al reanudarlo vuelve a `RECIBIDO`— o detener, y entonces `DETENIENDO` dura hasta que el servidor
 * suelta el lote. Un lote grande vuelve a `RECIBIDO` entre un tramo y el siguiente (ADR-0080).
 * Mientras esté abierto, la lista se refresca sola.
 */
export type EstadoLote =
  'RECIBIDO' | 'PROCESANDO' | 'PAUSADO' | 'DETENIENDO' | 'TERMINADO' | 'DETENIDO' | 'ERROR';

export const ESTADOS_ABIERTOS: readonly EstadoLote[] = [
  'RECIBIDO',
  'PROCESANDO',
  'PAUSADO',
  'DETENIENDO',
];

/** Lo que el panel le puede pedir a un lote abierto. */
export type OrdenIngesta = 'pausar' | 'reanudar' | 'detener';

/** Las cifras con las que termina un lote. Todas en cero mientras corre. */
export interface ResumenIngesta {
  readonly mensajesLeidos: number;
  readonly mensajesNuevos: number;
  readonly mensajesIgnorados: number;
  readonly publicaciones: number;
  readonly borradoresNuevos: number;
  readonly renovaciones: number;
  readonly agotados: number;
  readonly descartes: number;
  readonly alertas: number;
}

export interface LoteIngesta {
  readonly id: string;
  readonly proveedorId: string;
  readonly estado: EstadoLote;
  /** ISO-8601, UTC. Se formatea en Bogotá al pintarlo. */
  readonly creadoEn: string;
  readonly iniciadoEn: string | null;
  readonly terminadoEn: string | null;
  readonly resumen: ResumenIngesta;
  /**
   * Solo en `ERROR`: por qué no terminó, como código que el panel traduce (regla dura 4). El
   * servidor no manda frases desde el 11 de octubre de 2026.
   */
  readonly codigoError: CodigoDeFallo | null;
  /** El valor que interpola el texto del fallo, si hay uno: la fecha ilegible, el último fallo. */
  readonly datoError: string | null;
}

/**
 * Los motivos con que falla un lote, los mismos de `MotivoDeFalloDeLote` en el servidor. Cada uno
 * tiene su texto en `admin.ingestas.fallos`. Uno que el panel no conozca —el servidor agregó otro
 * antes de que el panel se enterara— se muestra como `DESCONOCIDO`, no como el código crudo.
 */
export const CODIGOS_DE_FALLO = [
  'ZIP_INVALIDO',
  'ZIP_SIN_CHAT',
  'ZIP_DEMASIADO_GRANDE',
  'NO_ES_UN_CHAT',
  'FECHA_ILEGIBLE',
  'ESTRUCTURA_NO_ACORDADA',
  'EXPORTACION_NO_ENCONTRADA',
  'SIN_ARCHIVO',
  'PROVEEDOR_NO_ENCONTRADO',
  'COLA_LLENA',
  'COLA_NO_DISPONIBLE',
  'SIN_REINTENTOS',
  'REINICIO',
  'ERROR_INESPERADO',
  'DESCONOCIDO',
] as const;

export type CodigoDeFallo = (typeof CODIGOS_DE_FALLO)[number];

export function esCodigoDeFallo(valor: string | null | undefined): valor is CodigoDeFallo {
  return (CODIGOS_DE_FALLO as readonly string[]).includes(valor ?? '');
}

/**
 * La clave y los parámetros del texto que explica el fallo. Sin reintentos y con el código del
 * último fallo, el texto lo nombra, traducido también.
 */
export function textoDelFallo(
  lote: LoteIngesta,
): { clave: string; parametros: Record<string, string>; ultimo: CodigoDeFallo | null } | null {
  if (!lote.codigoError) {
    return null;
  }
  if (lote.codigoError === 'SIN_REINTENTOS' && esCodigoDeFallo(lote.datoError)) {
    return {
      clave: 'admin.ingestas.fallos.SIN_REINTENTOS_CON_CAUSA',
      parametros: {},
      ultimo: lote.datoError,
    };
  }
  return {
    clave: 'admin.ingestas.fallos.' + lote.codigoError,
    parametros: { dato: lote.datoError ?? '' },
    ultimo: null,
  };
}

export interface LotesPaginados {
  readonly items: readonly LoteIngesta[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalLotes: number;
}

export interface FiltroLotes {
  /** Vacío = todos los proveedores. */
  readonly proveedorId: string;
  readonly pagina: number;
}

/** El archivo que exporta WhatsApp: un `.zip` con el `.txt` del chat y las fotos. */
export interface SubirExportacion {
  readonly proveedorId: string;
  readonly archivo: File;
}

/** Lo que dejó borrar una ingesta: cuántos productos se fueron y cuántos se quedaron publicados. */
export interface LoteEliminado {
  readonly productosEliminados: number;
  readonly productosConservados: number;
}

export function loteAbierto(lote: LoteIngesta): boolean {
  return ESTADOS_ABIERTOS.includes(lote.estado);
}

/** En la cola no hay trabajo que pausar: el servidor solo pausa lo que se está procesando. */
export function sePuedePausar(lote: LoteIngesta): boolean {
  return lote.estado === 'PROCESANDO';
}

export function sePuedeReanudar(lote: LoteIngesta): boolean {
  return lote.estado === 'PAUSADO';
}

export function sePuedeDetener(lote: LoteIngesta): boolean {
  return lote.estado === 'RECIBIDO' || lote.estado === 'PROCESANDO' || lote.estado === 'PAUSADO';
}

/**
 * ¿Hay cifras que mostrar? Las de lo que terminó o falló, y las de lo que se detuvo después de
 * empezar. Uno detenido en la cola nunca se leyó: sus ceros no dicen nada.
 */
export function tieneResumen(lote: LoteIngesta): boolean {
  return (
    lote.estado === 'TERMINADO' ||
    lote.estado === 'ERROR' ||
    (lote.estado === 'DETENIDO' && lote.iniciadoEn !== null)
  );
}
