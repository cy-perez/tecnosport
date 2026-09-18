package co.tecnosport.api.application.envio;

/**
 * El proveedor respondió, y respondió que nuestro cuerpo está mal. No hay envío a domicilio para
 * este carrito y este destino, y <strong>reintentar no lo arregla</strong>: Skydropx deduplica las
 * cotizaciones por contenido, así que la misma pregunta devuelve el mismo rechazo (docs/13 §6.9).
 * El checkout lo traduce a recogida en el punto.
 *
 * <p>Se separa de {@link CotizacionNoDisponibleException} justo por eso. Aquélla dice "no pudimos
 * preguntar" y el reintento acierta; ésta dice "preguntamos mal", y hasta el 17 de septiembre de
 * 2026 las dos viajaban juntas como un 503, invitando al comprador a reintentar algo imposible. Es
 * la misma clase de fallo silencioso que el valor declarado por debajo del mínimo antes de
 * adr/0035: ventas que no ocurren sin un error que las explique.
 *
 * <p>Viaja como <strong>409</strong> y no como 503, con el mismo criterio que sus dos hermanas de
 * negocio —{@link EnvioSinCoberturaException} y {@link ArticuloNoAsegurableException}—: la
 * solicitud está bien formada, el servicio del que dependemos está arriba, y lo que falla no se
 * arregla repitiendo la llamada. Un 503 le prometería al cliente que reintentar sirve.
 *
 * <p><strong>Para quien opera es un defecto nuestro, no una condición del mundo</strong>, y por eso
 * el adaptador lo registra en {@code error} con los nombres de los campos que la plataforma
 * rechazó. El comprador no ve nada de eso: publicar la forma en que falla un proveedor no le sirve
 * a nadie, y los valores rechazados son su teléfono y su dirección (docs/08-seguridad-legal.md).
 */
public final class CotizacionRechazadaException extends RuntimeException {

  public CotizacionRechazadaException() {
    super("No es posible calcular el envío a domicilio de este pedido.");
  }
}
