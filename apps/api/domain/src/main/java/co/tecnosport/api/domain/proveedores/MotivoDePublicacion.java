package co.tecnosport.api.domain.proveedores;

/**
 * Por qué una publicación no dejó un borrador: se descartó —se leyó y no hay qué revisar— o falló
 * —no se pudo leer—. Es un código que el panel traduce (regla dura 4), como {@link
 * MotivoDeFalloDeLote}; hasta el 11 de octubre de 2026 era una frase en español que nadie veía,
 * porque el panel no mostraba las publicaciones de un lote.
 */
public enum MotivoDePublicacion {
  /**
   * El extractor leyó el mensaje y no anunciaba un producto: un saludo, un aviso, una promoción.
   */
  SIN_PRODUCTO,
  /** Anuncia como agotado algo que el catálogo no tiene: no hay nada que marcar. */
  AGOTADO_FUERA_DE_CATALOGO,
  /**
   * La misma referencia de un borrador que espera revisión. El dato, si lo hay, es cuántas fotos se
   * le sumaron a ese borrador.
   */
  MISMA_REFERENCIA_EN_REVISION,
  /** El mismo texto, con la misma foto, de un borrador que espera revisión. */
  ANUNCIO_REPETIDO,
  /** El anuncio está en el chat de caballero del proveedor, y se procesa desde ese archivo. */
  VIENE_EN_CHAT_DE_CABALLERO,
  /** El extractor no pudo: la API rechazó la petición, se negó, o respondió algo ilegible. */
  EXTRACCION_FALLIDA,
  /** Algo falló al decidir qué hacer con lo extraído; el dato es el tipo de la excepción. */
  ERROR_AL_RESOLVER,
  /** Una publicación de antes de los códigos cuyo motivo no se pudo reconocer al migrar. */
  DESCONOCIDO
}
