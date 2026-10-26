package co.tecnosport.api.application.pedido;

/**
 * Qué formas de entrega ofrece hoy el negocio. El envío a domicilio siempre; la recogida en el
 * punto de Medellín solo si está encendida, y desde el 8 de octubre de 2026 arranca apagada.
 *
 * <p>Apagarla no es esconder una opción: es lo único que permitía comprar en línea un carrito que
 * no puede ir a domicilio —un artículo que no se puede asegurar (ADR-0036), uno sin medidas
 * (ADR-0046), un destino sin cobertura o una cotización rechazada—. Con la recogida apagada esos
 * carritos no se compran en el sitio y el checkout remite a WhatsApp.
 *
 * <p>Desde el 26 de octubre de 2026 es el interruptor general y no la respuesta entera: encendida,
 * la recogida se ofrece solo al carrito que viene todo de proveedores locales. Esa regla y su 409
 * viven en {@link RecogidaEnPunto}.
 */
public record ModalidadesDeEntrega(boolean retiroEnPunto) {

  /**
   * Siempre, hoy. Vive aquí y no en el controlador que lo publica para que, el día que deje de ser
   * siempre, el sitio donde cambiarlo sea el mismo donde se decide la recogida.
   */
  public boolean envioADomicilio() {
    return true;
  }
}
