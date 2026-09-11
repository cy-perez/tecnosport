package co.tecnosport.api.infrastructure.envio;

/**
 * El mapeo con Skydropx todavía no está confirmado contra la cuenta real, así que no se puede
 * cotizar. No es un error de negocio ni una falla del proveedor: es una pieza que falta.
 *
 * <p>{@link SkydropxClient} la atrapa y devuelve una lista vacía de tarifas, que para el checkout
 * significa lo mismo que "el proveedor no respondió": no hay envío a domicilio, solo recogida en el
 * punto. Fallar cerrado por una pieza que falta es exactamente lo que adr/0021 decidió para cuando
 * no hay tarifa.
 */
final class MapeoSinConfirmarException extends RuntimeException {

  MapeoSinConfirmarException(String mensaje) {
    super(mensaje);
  }
}
