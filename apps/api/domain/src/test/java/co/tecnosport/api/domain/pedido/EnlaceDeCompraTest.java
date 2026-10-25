package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnlaceDeCompraTest {

  private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");
  private static final Duration UN_DIA = Duration.ofHours(24);

  private static EnlaceDeCompra nuevo() {
    return EnlaceDeCompra.generar(UUID.randomUUID(), UUID.randomUUID(), UN_DIA, AHORA);
  }

  @Test
  void vence_a_las_24_horas_y_no_antes() {
    EnlaceDeCompra enlace = nuevo();

    assertEquals(AHORA.plus(UN_DIA), enlace.expiraEn());
    assertTrue(enlace.vigente(AHORA.plus(UN_DIA).minusSeconds(1)));
    assertFalse(enlace.vigente(AHORA.plus(UN_DIA)));
  }

  @Test
  void cada_enlace_lleva_un_token_distinto_y_que_cabe_en_una_url() {
    EnlaceDeCompra uno = nuevo();
    EnlaceDeCompra otro = nuevo();

    assertNotEquals(uno.token(), otro.token());
    assertTrue(uno.token().matches("[A-Za-z0-9_-]{43}"), uno.token());
  }

  @Test
  void sirve_para_una_sola_compra() {
    EnlaceDeCompra enlace = nuevo();
    UUID pedido = UUID.randomUUID();

    enlace.usar(pedido, AHORA.plusSeconds(60));

    assertEquals(pedido, enlace.pedidoId().orElseThrow());
    assertFalse(enlace.vigente(AHORA.plusSeconds(61)));
    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () -> enlace.usar(UUID.randomUUID(), AHORA.plusSeconds(120)));
  }

  @Test
  void vencido_no_compra() {
    EnlaceDeCompra enlace = nuevo();

    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () -> enlace.usar(UUID.randomUUID(), AHORA.plus(UN_DIA)));
  }

  @Test
  void anulado_no_compra_y_usado_no_se_anula() {
    EnlaceDeCompra anulado = nuevo();
    anulado.anular(AHORA.plusSeconds(10));
    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () -> anulado.usar(UUID.randomUUID(), AHORA.plusSeconds(20)));
    assertThrows(ExcepcionDeDominio.class, () -> anulado.anular(AHORA.plusSeconds(30)));

    EnlaceDeCompra usado = nuevo();
    usado.usar(UUID.randomUUID(), AHORA.plusSeconds(10));
    assertThrows(ExcepcionDeDominio.class, () -> usado.anular(AHORA.plusSeconds(20)));
  }
}
