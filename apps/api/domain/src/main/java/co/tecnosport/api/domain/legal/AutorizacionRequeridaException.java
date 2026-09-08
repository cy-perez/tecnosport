package co.tecnosport.api.domain.legal;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Nadie se registra ni compra sin autorizar el tratamiento de sus datos (Ley 1581 de 2012). No es
 * una validación de formulario que el frontend pueda saltarse: la casilla del navegador es una
 * comodidad, y esta excepción es la regla.
 */
public class AutorizacionRequeridaException extends ExcepcionDeDominio {

  public AutorizacionRequeridaException() {
    super("Hay que autorizar el tratamiento de datos personales para continuar.");
  }
}
