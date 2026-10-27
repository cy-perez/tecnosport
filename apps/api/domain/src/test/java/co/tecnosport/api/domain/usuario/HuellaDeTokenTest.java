package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HuellaDeTokenTest {

  private static final UUID TOKEN = UUID.fromString("0192f4a0-0000-7000-8000-000000000001");

  /**
   * Calculado aparte ({@code hashlib.sha256(b'0192f4a0-...-000000000001').hexdigest()}). Que
   * coincida también con la expresión de la migración lo comprueba {@code
   * MigracionHuellaDeLosTokensTest} contra PostgreSQL de verdad.
   */
  @Test
  void esElSha256DelTextoDelUuidEnHexadecimal() {
    assertEquals(
        "72ca24a7b11c4fd960b28f3fa7c983d99d2e80dd1f75d9ea1af4e5073dc14186",
        HuellaDeToken.de(TOKEN));
  }

  @Test
  void laHuellaNoContieneElToken() {
    assertFalse(HuellaDeToken.de(TOKEN).contains("0192f4a0"));
  }
}
