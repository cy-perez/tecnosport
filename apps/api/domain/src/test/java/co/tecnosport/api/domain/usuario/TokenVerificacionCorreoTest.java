package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenVerificacionCorreoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VEINTICUATRO_HORAS = Duration.ofHours(24);

  private TokenVerificacionCorreo crear() {
    return TokenVerificacionCorreo.crear(UUID.randomUUID(), AHORA, VEINTICUATRO_HORAS);
  }

  @Test
  void unTokenReciénCreadoEstaVigente() {
    TokenVerificacionCorreo token = crear();

    assertTrue(token.estaVigente(AHORA.plusSeconds(1)));
  }

  @Test
  void unTokenVencidoNoEstaVigente() {
    TokenVerificacionCorreo token = crear();

    assertFalse(token.estaVigente(AHORA.plus(VEINTICUATRO_HORAS).plusSeconds(1)));
  }

  @Test
  void marcarUsadoDejaDeEstarVigente() {
    TokenVerificacionCorreo token = crear();

    token.marcarUsado(AHORA.plusSeconds(1));

    assertFalse(token.estaVigente(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoDosVecesLanzaInvalido() {
    TokenVerificacionCorreo token = crear();
    token.marcarUsado(AHORA.plusSeconds(1));

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> token.marcarUsado(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoSobreTokenVencidoLanzaInvalido() {
    TokenVerificacionCorreo token = crear();

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> token.marcarUsado(AHORA.plus(VEINTICUATRO_HORAS).plusSeconds(1)));
  }
}
