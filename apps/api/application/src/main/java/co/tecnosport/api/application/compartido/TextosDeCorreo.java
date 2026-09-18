package co.tecnosport.api.application.compartido;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Resuelve un {@link TextoDeCorreo} al texto que se manda, con sus datos dentro.
 *
 * <p>Puerto aparte de {@link EnviadorDeCorreo} porque son dos preguntas distintas: aquél sabe
 * <i>cómo</i> se manda un correo, éste sabe <i>qué dice</i>. Juntarlos habría dejado al enviador
 * decidiendo textos, que es justo lo que se acaba de sacar de los casos de uso.
 *
 * <p><b>Los argumentos se escapan para HTML por contrato</b>, no por cortesía de cada quien: los
 * cuerpos son HTML y algunos de estos datos los escribe una persona en el panel —el asunto de una
 * PQR, el motivo de una prórroga—. Con la concatenación anterior, un asunto con {@code <} rompía el
 * correo del comprador, y el mismo razonamiento ya se había aplicado al serializar el JSON-LD de la
 * ficha y no aquí. Escapando en un solo sitio, no hay ningún sitio donde olvidarlo.
 */
public interface TextosDeCorreo {

  String texto(TextoDeCorreo texto, Object... argumentos);

  /**
   * Un importe escrito como se escribe en el idioma en el que se va a pintar el correo.
   *
   * <p>Vive aquí y no en el caso de uso por lo mismo que los textos: <b>el agrupamiento de miles es
   * parte del idioma</b>. Nació dentro de {@code EnviarComprobantesDeCompra}, con un {@code
   * DecimalFormat} de separador fijo, y su propio javadoc confesaba el problema —"esto no sabe en
   * cuál idioma se va a pintar"—: el paquete inglés dice {@code COP {0}} y habría recibido {@code
   * 179.800}, que en inglés se lee como ciento setenta y nueve con ocho. Lo levantó una revisión
   * adversarial.
   */
  String dinero(Dinero valor);
}
