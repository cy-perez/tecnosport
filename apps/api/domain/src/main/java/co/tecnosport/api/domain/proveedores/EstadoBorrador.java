package co.tecnosport.api.domain.proveedores;

/**
 * En qué punto está un borrador. {@code RENOVACION_APLICADA} no es un borrador que alguien vaya a
 * revisar: es la constancia de que un mensaje reconoció un producto que ya existía y lo renovó.
 */
public enum EstadoBorrador {
  EN_REVISION,
  APROBADO,
  RECHAZADO,
  RENOVACION_APLICADA
}
