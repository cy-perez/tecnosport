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
  PRECIO_CAMBIO,
  /**
   * El mensaje anunciaba varios productos con las mismas fotos: quien aprueba elige cuáles son de
   * este y marca la principal, porque de esa sale la huella visual.
   */
  FOTOS_COMPARTIDAS,
  /**
   * El mensaje anuncia una réplica («1.1»): se publica con la marca Genérica y la original solo va
   * en el título, como «Camiseta estilo Puma - BMW». Quien aprueba confirma las dos cosas.
   */
  REPLICA
}
