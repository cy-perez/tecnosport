package co.tecnosport.api.application.envio;

/**
 * No se pudo cotizar, y no se sabe si hay cobertura. Se separa de {@link
 * EnvioSinCoberturaException} porque le pide al comprador cosas distintas: uno, cambiar la
 * dirección; este, volver a intentar (docs/13 §6.9, adr/0021).
 *
 * <p>Viaja como <strong>503</strong> y no como un 409: no es un conflicto con el estado del
 * negocio, es que el servicio del que depende esta respuesta no está disponible ahora mismo. El
 * cliente que reintenta acierta — la consulta del checkout ya lo hace una vez sola.
 *
 * <p>El motivo técnico no viaja en el mensaje: al comprador no le sirve y publicar la forma en que
 * falla un proveedor no le sirve a nadie más. Queda en el registro, con su {@link
 * ResultadoCotizacion.Motivo}.
 */
public final class CotizacionNoDisponibleException extends RuntimeException {

  public CotizacionNoDisponibleException() {
    super("No fue posible cotizar el envío en este momento. Vuelve a intentarlo.");
  }
}
