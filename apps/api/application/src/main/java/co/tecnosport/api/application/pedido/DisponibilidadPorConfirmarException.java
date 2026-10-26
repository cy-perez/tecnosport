package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * La variante se confirma con el proveedor antes de venderse —el color de un equipo de tecnología
 * (ADR-0086), la talla de un tenis (ADR-0089)— y el pedido no trae un enlace de compra. La ficha ya
 * no deja agregarla al carrito así; esto lo sostiene el servidor, que no le cree al cliente (regla
 * dura #7). Se llamaba {@code ColorPorConfirmarException} hasta el 25 de octubre de 2026.
 */
public final class DisponibilidadPorConfirmarException extends ExcepcionDeDominio {

  public DisponibilidadPorConfirmarException(String producto, String detalle) {
    super(
        "La disponibilidad de "
            + producto
            + (detalle == null || detalle.isBlank() ? "" : " (" + detalle + ")")
            + " se confirma con nosotros antes de comprarlo: escríbenos por WhatsApp y te enviamos"
            + " el enlace de compra.");
  }
}
