package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenRecuperacionClaveTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration TREINTA_MINUTOS = Duration.ofMinutes(30);

  private TokenRecuperacionClave crear() {
    return TokenRecuperacionClave.crear(UUID.randomUUID(), AHORA, TREINTA_MINUTOS);
  }

  @Test
  void unTokenReciénCreadoEstaVigente() {
    TokenRecuperacionClave token = crear();

    assertTrue(token.estaVigente(AHORA.plusSeconds(1)));
  }

  @Test
  void unTokenVencidoNoEstaVigente() {
    TokenRecuperacionClave token = crear();

    assertFalse(token.estaVigente(AHORA.plus(TREINTA_MINUTOS).plusSeconds(1)));
  }

  @Test
  void marcarUsadoDejaDeEstarVigente() {
    TokenRecuperacionClave token = crear();

    token.marcarUsado(AHORA.plusSeconds(1));

    assertFalse(token.estaVigente(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoDosVecesLanzaInvalido() {
    TokenRecuperacionClave token = crear();
    token.marcarUsado(AHORA.plusSeconds(1));

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () -> token.marcarUsado(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoSobreTokenVencidoLanzaInvalido() {
    TokenRecuperacionClave token = crear();

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () -> token.marcarUsado(AHORA.plus(TREINTA_MINUTOS).plusSeconds(1)));
  }
}
