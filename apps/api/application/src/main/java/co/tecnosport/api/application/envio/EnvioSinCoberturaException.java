package co.tecnosport.api.application.envio;

/**
 * No hay ninguna tarifa para ese destino. No es una falla del sistema ni del proveedor: hay
 * ciudades que hoy no se pueden despachar, y decirlo con un 502 sería mentir sobre de quién es el
 * problema. El checkout lo traduce a "solo recogida en el punto" (adr/0021, docs/03-api.md).
 *
 * <p>Llega aquí por tres caminos que el comprador no distingue y que dan lo mismo: ninguna
 * transportadora cubre el destino, el proveedor no respondió, o todas las tarifas que devolvió ya
 * estaban vencidas.
 */
public final class EnvioSinCoberturaException extends RuntimeException {

  public EnvioSinCoberturaException(String codigoDaneCiudad) {
    super("No hay tarifa de envío disponible para la ciudad " + codigoDaneCiudad + ".");
  }
}
