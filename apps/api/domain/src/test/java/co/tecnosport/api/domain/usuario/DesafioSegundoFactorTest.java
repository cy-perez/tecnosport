package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DesafioSegundoFactorTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final UUID USUARIO = UUID.randomUUID();
  private static final Duration CINCO_MINUTOS = Duration.ofMinutes(5);

  @Test
  void vencePasadaSuVigencia() {
    DesafioSegundoFactor desafio = DesafioSegundoFactor.crear(USUARIO, AHORA, CINCO_MINUTOS);
    assertEquals(AHORA.plus(CINCO_MINUTOS), desafio.expiraEn());
    assertTrue(desafio.estaVigente(AHORA.plusSeconds(299)));
    assertFalse(desafio.estaVigente(AHORA.plusSeconds(300)));
  }

  @Test
  void seUsaUnaSolaVez() {
    DesafioSegundoFactor desafio = DesafioSegundoFactor.crear(USUARIO, AHORA, CINCO_MINUTOS);
    desafio.marcarUsado(AHORA);
    assertThrows(DesafioSegundoFactorInvalidoException.class, () -> desafio.marcarUsado(AHORA));
  }

  @Test
  void vencidoNoSeUsa() {
    DesafioSegundoFactor desafio = DesafioSegundoFactor.crear(USUARIO, AHORA, CINCO_MINUTOS);
    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () -> desafio.marcarUsado(AHORA.plus(CINCO_MINUTOS)));
  }
}
