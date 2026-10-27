package co.tecnosport.api.domain.usuario;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

/**
 * Un código de respaldo del segundo factor: la puerta para el día que el teléfono se pierde. 16
 * caracteres Base32 —80 bits de {@code SecureRandom}—, mostrados en cuatro grupos de cuatro ({@code
 * ABCD-EFGH-IJKL-MNOP}) para que se puedan copiar a mano sin perderse.
 *
 * <p>Solo existe en claro dos veces: al generarlo, para mostrarlo una sola vez, y al leer lo que
 * una persona teclea. Lo que se guarda es su {@link #huella()}, igual que los tokens de sesión: con
 * 80 bits aleatorios no hay diccionario contra el que probar, así que un SHA-256 sin sal basta y
 * deja buscar por la huella.
 */
public final class CodigoDeRespaldo {

  public static final int LONGITUD = 16;
  private static final SecureRandom ALEATORIO = new SecureRandom();

  /** Normalizado: 16 caracteres del alfabeto Base32, en mayúsculas, sin guiones. */
  private final String valor;

  private CodigoDeRespaldo(String valor) {
    this.valor = valor;
  }

  public static CodigoDeRespaldo generar() {
    byte[] bytes = new byte[LONGITUD * 5 / 8];
    ALEATORIO.nextBytes(bytes);
    return new CodigoDeRespaldo(Base32.codificar(bytes));
  }

  /**
   * Lo que una persona tecleó, si tiene la forma de un código de respaldo: ignora mayúsculas,
   * espacios y guiones. Vacío si no la tiene, que no es lo mismo que "no vale": eso lo decide el
   * agregado contra sus huellas.
   */
  public static Optional<CodigoDeRespaldo> leer(String tecleado) {
    if (tecleado == null) {
      return Optional.empty();
    }
    String normalizado = Base32.normalizar(tecleado);
    if (normalizado.length() != LONGITUD || !Base32.esDelAlfabeto(normalizado)) {
      return Optional.empty();
    }
    return Optional.of(new CodigoDeRespaldo(normalizado));
  }

  /** {@code ABCD-EFGH-IJKL-MNOP}. */
  public String mostrado() {
    return valor.substring(0, 4)
        + "-"
        + valor.substring(4, 8)
        + "-"
        + valor.substring(8, 12)
        + "-"
        + valor.substring(12, 16);
  }

  public String huella() {
    return HuellaDeToken.de(valor);
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof CodigoDeRespaldo c && valor.equals(c.valor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valor);
  }

  @Override
  public String toString() {
    return "CodigoDeRespaldo[censurado]";
  }
}
