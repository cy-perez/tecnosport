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

/**
 * Lo que el panel le puede pedir a un lote: pausar, reanudar o detener uno abierto, y reintentar
 * las fallidas de uno terminado.
 */
export type OrdenIngesta = 'pausar' | 'reanudar' | 'detener' | 'reintentarFallidas';

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
  /**
   * Las publicaciones que no se pudieron leer —la API del modelo las rechazó—. Hasta el 11 de
   * octubre de 2026 se contaban como descartadas, que es decir que no eran productos.
   */
  readonly fallidas: number;
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
   * En `ERROR`, por qué no terminó; en `PAUSADO`, por qué se pausó solo —la cuenta del modelo no
   * respondía—. Un código que el panel traduce (regla dura 4): el servidor no manda frases desde el
   * 11 de octubre de 2026.
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
  'EXTRACTOR_NO_DISPONIBLE',
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

/**
 * Por qué una publicación no dejó borrador, los mismos de `MotivoDePublicacion` en el servidor. Cada
 * uno tiene su texto en `admin.ingestas.cerradas.motivos`; uno que el panel no conozca se muestra
 * como `DESCONOCIDO`.
 */
export const MOTIVOS_DE_PUBLICACION = [
  'SIN_PRODUCTO',
  'AGOTADO_FUERA_DE_CATALOGO',
  'MISMA_REFERENCIA_EN_REVISION',
  'ANUNCIO_REPETIDO',
  'VIENE_EN_CHAT_DE_CABALLERO',
  'TIPO_EXCLUIDO',
  'PRECIO_SOBRE_TOPE',
  'CALZADO_QUE_NO_ES_TENIS',
  'CALZADO_SIN_FOTOS_LEIDAS',
  'EXTRACCION_FALLIDA',
  'ERROR_AL_RESOLVER',
  'DESCONOCIDO',
] as const;

export type MotivoDePublicacion = (typeof MOTIVOS_DE_PUBLICACION)[number];

export function esMotivoDePublicacion(
  valor: string | null | undefined,
): valor is MotivoDePublicacion {
  return (MOTIVOS_DE_PUBLICACION as readonly string[]).includes(valor ?? '');
}

/** Una publicación del lote que no dejó borrador: descartada o fallida, con su motivo. */
export interface PublicacionCerrada {
  readonly id: string;
  /** ISO-8601, UTC: la fecha del anuncio en el chat. */
  readonly fecha: string;
  readonly estado: 'DESCARTADA' | 'ERROR';
  readonly motivo: MotivoDePublicacion;
  /**
   * El valor que interpola el texto del motivo: cuántas fotos se sumaron, el tipo del error, o el
   * tipo de prenda que no se le compra al proveedor.
   */
  readonly dato: string | null;
  /** El comienzo del anuncio; nulo si eran solo fotos. */
  readonly extracto: string | null;
  readonly fotos: number;
}

/**
 * La clave del texto que explica el motivo. «La misma referencia» con fotos sumadas lo dice,
 * porque esas fotos no se perdieron: están en el otro borrador.
 */
export function claveDelMotivo(publicacion: PublicacionCerrada): string {
  const base = 'admin.ingestas.cerradas.motivos.' + publicacion.motivo;
  if (publicacion.motivo === 'MISMA_REFERENCIA_EN_REVISION' && publicacion.dato) {
    return base + '_CON_FOTOS';
  }
  // Las fotos se leyeron y ninguna mostró un calzado reconocible: no hay tipo que nombrar.
  if (publicacion.motivo === 'CALZADO_QUE_NO_ES_TENIS' && !publicacion.dato) {
    return base + '_SIN_TIPO';
  }
  return base;
}

/** ¿Hay algo cerrado que mirar? Solo cuando el lote ya contó. */
export function tieneCerradas(lote: LoteIngesta): boolean {
  return tieneResumen(lote) && lote.resumen.descartes + lote.resumen.fallidas > 0;
}

/**
 * Las fallidas se reintentan en un lote terminado: en uno abierto todavía pueden estar corriendo, y
 * en uno detenido lo que faltó no se retoma (ver `admin.ingestas.ordenes.loQueImplica`).
 */
export function sePuedenReintentarFallidas(lote: LoteIngesta): boolean {
  return lote.estado === 'TERMINADO' && lote.resumen.fallidas > 0;
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
