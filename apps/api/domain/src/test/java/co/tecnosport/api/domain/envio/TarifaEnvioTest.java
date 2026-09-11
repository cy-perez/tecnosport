package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TarifaEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant VENCE = AHORA.plusSeconds(86_400);

  private static TarifaEnvio tarifa(String id, long costo, int dias) {
    return new TarifaEnvio(id, "Servientrega", "Estándar", Dinero.deCop(costo), dias, false, VENCE);
  }

  @Test
  void rechazaCostoNegativo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new TarifaEnvio("t1", "Envía", "Estándar", Dinero.deCop(-1), 3, false, VENCE));
  }

  @Test
  void rechazaPlazoNegativo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new TarifaEnvio("t1", "Envía", "Estándar", Dinero.deCop(15_000), -1, false, VENCE));
  }

  @Test
  void rechazaTransportadoraVacia() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new TarifaEnvio("t1", "  ", "Estándar", Dinero.deCop(15_000), 3, false, VENCE));
  }

  /** Un flete en cero es válido: una promoción de envío gratis lo es. Cero no es negativo. */
  @Test
  void aceptaCostoEnCero() {
    assertEquals(Dinero.deCop(0), tarifa("t1", 0, 2).costo());
  }

  @Test
  void estaVigenteAntesDelVencimiento() {
    assertTrue(tarifa("t1", 15_000, 3).estaVigente(AHORA));
  }

  /**
   * El instante exacto del vencimiento ya no vale. Una tarifa que vence "a las 12:00" no sirve a
   * las 12:00: ese es el primer momento en que dejó de valer.
   */
  @Test
  void noEstaVigenteEnElInstanteDelVencimiento() {
    assertFalse(tarifa("t1", 15_000, 3).estaVigente(VENCE));
  }

  @Test
  void masEconomicaEligeElMenorCosto() {
    List<TarifaEnvio> tarifas =
        List.of(tarifa("cara", 30_000, 1), tarifa("barata", 12_000, 5), tarifa("media", 20_000, 3));

    assertEquals("barata", TarifaEnvio.masEconomica(tarifas).orElseThrow().idTarifa());
  }

  /** Empate a costo: gana el plazo menor, no el orden en que el proveedor las devolvió. */
  @Test
  void masEconomicaDesempataPorElPlazoMenor() {
    List<TarifaEnvio> tarifas = List.of(tarifa("lenta", 12_000, 6), tarifa("rapida", 12_000, 2));

    assertEquals("rapida", TarifaEnvio.masEconomica(tarifas).orElseThrow().idTarifa());
  }

  @Test
  void masEconomicaDeUnaListaVaciaEsVacio() {
    assertEquals(Optional.empty(), TarifaEnvio.masEconomica(List.of()));
  }
}
