package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Resuelve un {@link TextoDeDifusion} al texto que se publica, con sus datos dentro.
 *
 * <p>Sin escape de ningún tipo, al revés que {@code TextosDeCorreo}: el pie de una publicación es
 * texto plano, y Meta pinta un {@code <} como un {@code <}.
 */
public interface TextosDeDifusion {

  String texto(TextoDeDifusion texto, Object... argumentos);

  /** Un importe con el agrupamiento de miles del idioma en el que se publica, sin el símbolo. */
  String dinero(Dinero valor);
}
