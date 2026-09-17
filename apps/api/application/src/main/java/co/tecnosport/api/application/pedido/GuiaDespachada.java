package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Una guía del despacho: transportadora, número y lo que esa guía nos cuesta.
 *
 * <p>Van varias porque ninguna transportadora colombiana admite multipaquete y un pedido de dos
 * variantes son dos guías (adr/0031). {@code transportadora} y {@code guia} se validan en {@code
 * GuiaEnvio}, no aquí: no hace falta duplicar la regla.
 *
 * <p>{@code codigoTransportadora} y {@code urlEtiqueta} <strong>solo vienen cuando la guía la
 * emitimos nosotros</strong> (adr/0033). Una guía tecleada en el panel llega con los dos en nulo, y
 * no es un dato que falte por descuido: quien despachó pudo emitirla en la web de la transportadora
 * y esa guía puede no existir en la plataforma. Lo que decide si se puede conciliar su rastreo no
 * es quién la lleva, es si la emitimos nosotros.
 */
public record GuiaDespachada(
    String transportadora,
    String codigoTransportadora,
    String guia,
    Dinero costoEnvio,
    String urlEtiqueta) {

  /** La guía que teclea una persona en el panel: sin código de plataforma y sin rótulo. */
  public GuiaDespachada(String transportadora, String guia, Dinero costoEnvio) {
    this(transportadora, null, guia, costoEnvio, null);
  }
}
