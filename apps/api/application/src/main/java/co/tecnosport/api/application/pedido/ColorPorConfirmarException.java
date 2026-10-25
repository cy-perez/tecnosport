package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * La variante tiene el color por confirmar con el proveedor y el pedido no trae un enlace de compra
 * (ADR-0086). La ficha ya no deja agregarla al carrito así; esto lo sostiene el servidor, que no le
 * cree al cliente (regla dura #7).
 */
public final class ColorPorConfirmarException extends ExcepcionDeDominio {

  public ColorPorConfirmarException(String producto, String detalle) {
    super(
        "El color de "
            + producto
            + (detalle == null || detalle.isBlank() ? "" : " (" + detalle + ")")
            + " se confirma con nosotros antes de comprarlo: escríbenos por WhatsApp y te enviamos"
            + " el enlace de compra.");
  }
}
