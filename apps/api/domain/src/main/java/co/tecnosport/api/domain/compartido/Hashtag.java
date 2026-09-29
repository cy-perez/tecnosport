package co.tecnosport.api.domain.compartido;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Una etiqueta de red social, con su almohadilla incluida en {@code valor}.
 *
 * <p><b>Vive en {@code compartido} y no en {@code difusion}</b>, aunque solo la difusión la
 * publique: quien la <em>guarda</em> es la {@code Categoria}, que es del catálogo. Ponerla en
 * difusión habría obligado al catálogo a importar el paquete de difusión para declarar un campo
 * suyo, y entonces borrar la difusión algún día dejaría al catálogo sin compilar. Es el mismo sitio
 * y por la misma razón que {@link Slug}.
 *
 * <p><b>Se guarda con la almohadilla y no sin ella.</b> Las dos formas funcionan; lo que decide es
 * quién lee la fila. Sin almohadilla, una columna con {@code Parlantes} no se distingue de un
 * nombre y alguien acaba preguntándose si el armador del pie la pone o no. Con ella, la fila dice
 * exactamente lo que se va a publicar. Quien escribe puede teclearla o no: se normaliza al entrar.
 *
 * <p><b>Admite tildes y eñes a propósito.</b> {@code #Medellín} es una etiqueta real y usada, y
 * quitarle la tilde la convertiría en otra distinta, con otras publicaciones detrás. Lo que no
 * admite es nada que la rompa en dos: ni espacios, ni puntos, ni guiones — las redes cortan la
 * etiqueta en el primer carácter que no sea letra, dígito o guión bajo, así que {@code #ropa-dama}
 * se publicaría como {@code #ropa} y el resto sería texto suelto. Mejor que falle aquí, donde
 * alguien puede arreglarlo, que en silencio dentro de un post.
 */
public record Hashtag(String valor) {

  /**
   * El tope de las redes es de 150 caracteres. No es un dato de negocio ni cambia con el proveedor
   * —una etiqueta de 150 letras no la escribe nadie a mano—, así que se queda aquí como cordura
   * básica en vez de viajar a la configuración.
   */
  public static final int MAXIMO_CARACTERES = 150;

  private static final Pattern FORMATO = Pattern.compile("^#[\\p{L}\\p{N}_]+$");

  /** Lo que no cabe en una etiqueta: todo lo que no sea letra, dígito o guión bajo. */
  private static final Pattern NO_APTO = Pattern.compile("[^\\p{L}\\p{N}_]");

  public Hashtag {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio("Una etiqueta vacía no es una etiqueta.");
    }
    valor = valor.strip();
    if (!valor.startsWith("#")) {
      valor = "#" + valor;
    }
    if (valor.length() > MAXIMO_CARACTERES) {
      throw new ExcepcionDeDominio(
          "Una etiqueta no puede pasar de " + MAXIMO_CARACTERES + " caracteres: " + valor);
    }
    if (!FORMATO.matcher(valor).matches()) {
      throw new ExcepcionDeDominio(
          "Etiqueta inválida: "
              + valor
              + ". Solo admite letras, dígitos y guión bajo — un espacio o un guión la partiría en"
              + " dos al publicarla.");
    }
  }

  /**
   * La etiqueta que le corresponde a un nombre comercial, si es que le corresponde alguna.
   *
   * <p>Existe para una sola cosa: que el pie de un producto de JBL lleve {@code #JBL} sin que nadie
   * tenga que escribirlo en cada categoría. "Tommy Hilfiger" sale {@code #TommyHilfiger}, que es lo
   * que una persona habría tecleado.
   *
   * <p><b>Devuelve {@link Optional} y no lanza, al revés que el constructor</b>, y la diferencia es
   * de quién tiene la culpa. Una etiqueta que alguien escribe mal en el panel es un error que hay
   * que enseñarle; una marca cuyo nombre no da para etiqueta —"3M™", "+Sport"— no es un error de
   * nadie y no puede impedir que el producto se publique. Ahí simplemente no hay etiqueta de marca.
   */
  public static Optional<Hashtag> deNombreComercial(String nombre) {
    if (nombre == null) {
      return Optional.empty();
    }
    String limpio = NO_APTO.matcher(nombre).replaceAll("");
    if (limpio.isEmpty() || limpio.length() + 1 > MAXIMO_CARACTERES) {
      return Optional.empty();
    }
    return Optional.of(new Hashtag(limpio));
  }

  @Override
  public String toString() {
    return valor;
  }
}
