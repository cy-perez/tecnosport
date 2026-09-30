package co.tecnosport.api.domain.proveedores;

/**
 * Lo que un borrador tiene que resolver una persona antes de publicarse. Un borrador con cualquiera
 * de estas nunca se publica solo.
 */
public enum AlertaBorrador {
  /**
   * El precio del extractor y el de la expresión regular no coinciden, o el extractor no lo dio.
   */
  PRECIO_INCONSISTENTE,
  /** Ni el extractor ni la expresión regular encontraron precio. */
  SIN_PRECIO,
  TITULO_VACIO,
  /** El extractor no supo qué es. */
  TIPO_DESCONOCIDO,
  CONFIANZA_BAJA,
  /** La publicación no trae ninguna foto con archivo. */
  SIN_FOTOS,
  /** En una renovación: el proveedor cambió el precio y el catálogo tiene que revisarse. */
  PRECIO_CAMBIO
}
