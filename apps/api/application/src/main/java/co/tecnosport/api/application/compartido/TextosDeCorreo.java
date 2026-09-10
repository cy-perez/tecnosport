package co.tecnosport.api.application.compartido;

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
}
