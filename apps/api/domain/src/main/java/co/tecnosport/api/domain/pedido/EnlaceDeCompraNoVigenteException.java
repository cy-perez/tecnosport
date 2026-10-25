package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El enlace de compra no existe, venció, ya se usó o se anuló. Un solo mensaje para todo, como el
 * de los tokens de la cuenta: a quien prueba enlaces no se le dice cuál de las cuatro cosas pasó.
 */
public final class EnlaceDeCompraNoVigenteException extends ExcepcionDeDominio {

  public EnlaceDeCompraNoVigenteException() {
    super(
        "El enlace de compra ya no está vigente: escríbenos por WhatsApp y te confirmamos el color"
            + " de nuevo.");
  }
}
