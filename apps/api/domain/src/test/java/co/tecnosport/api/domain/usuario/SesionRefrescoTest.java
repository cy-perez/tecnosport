package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SesionRefrescoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration TREINTA_DIAS = Duration.ofDays(30);

  private SesionRefresco crear() {
    return SesionRefresco.crear(UUID.randomUUID(), UUID.randomUUID(), AHORA, TREINTA_DIAS);
  }

  @Test
  void unaSesionReciénCreadaEstaVigente() {
    SesionRefresco sesion = crear();

    assertTrue(sesion.estaVigente(AHORA.plusSeconds(1)));
  }

  @Test
  void unaSesionVencidaNoEstaVigente() {
    SesionRefresco sesion = crear();

    assertFalse(sesion.estaVigente(AHORA.plus(TREINTA_DIAS).plusSeconds(1)));
  }

  @Test
  void marcarUsadoDejaDeEstarVigente() {
    SesionRefresco sesion = crear();

    sesion.marcarUsado(AHORA.plusSeconds(1));

    assertFalse(sesion.estaVigente(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoDosVecesLanzaReutilizada() {
    SesionRefresco sesion = crear();
    sesion.marcarUsado(AHORA.plusSeconds(1));

    assertThrows(
        SesionRefrescoReutilizadaException.class, () -> sesion.marcarUsado(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoSobreSesionRevocadaLanzaReutilizada() {
    SesionRefresco sesion = crear();
    sesion.revocar(AHORA.plusSeconds(1));

    assertThrows(
        SesionRefrescoReutilizadaException.class, () -> sesion.marcarUsado(AHORA.plusSeconds(2)));
  }

  @Test
  void marcarUsadoSobreSesionVencidaSinUsarLanzaVencida() {
    SesionRefresco sesion = crear();

    assertThrows(
        SesionRefrescoVencidaException.class,
        () -> sesion.marcarUsado(AHORA.plus(TREINTA_DIAS).plusSeconds(1)));
  }

  @Test
  void revocarDosVecesEsInofensivo() {
    SesionRefresco sesion = crear();

    sesion.revocar(AHORA.plusSeconds(1));
    sesion.revocar(AHORA.plusSeconds(2));

    assertFalse(sesion.estaVigente(AHORA.plusSeconds(3)));
  }
}
