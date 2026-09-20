package co.tecnosport.api.domain.compartido;

/**
 * Los tipos de documento que acepta la pasarela de Sistecrédito (guía {@code G-ALI-10}, "Términos
 * para body": {@code TI, CC, TIE, NIT}). No son todos los que existen en Colombia —falta la cédula
 * de extranjería como tal, el pasaporte, el PPT— y eso es a propósito: este enum enumera lo que el
 * único consumidor que hoy lo pide sabe recibir, no un catálogo general de documentos.
 *
 * <p>El día que otro sitio necesite un tipo que Sistecrédito no acepta, el enum crece y es la
 * traducción hacia la pasarela la que tiene que decidir qué hacer con el valor nuevo.
 */
public enum TipoDocumento {
  /** Cédula de ciudadanía. */
  CC,
  /** Tarjeta de identidad. */
  TI,
  /** Tarjeta de identidad de extranjero. */
  TIE,
  /** Número de identificación tributaria. */
  NIT
}
