package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NumeroPedidoTest {

  @Test
  void deFormateaPrefijoAnioYSecuencialConCeros() {
    NumeroPedido numero = NumeroPedido.de(2026, 123);

    assertEquals("TS-2026-000123", numero.valor());
  }

  @Test
  void deConSecuencialEnCeroSeRechaza() {
    assertThrows(NumeroPedidoInvalidoException.class, () -> NumeroPedido.de(2026, 0));
  }

  @Test
  void deConSecuencialNegativoSeRechaza() {
    assertThrows(NumeroPedidoInvalidoException.class, () -> NumeroPedido.de(2026, -1));
  }

  @Test
  void deConSecuencialSobreElMaximoSeRechaza() {
    assertThrows(NumeroPedidoInvalidoException.class, () -> NumeroPedido.de(2026, 1_000_000));
  }

  @Test
  void deConSecuencialEnElMaximoEsValido() {
    NumeroPedido numero = NumeroPedido.de(2026, 999_999);

    assertEquals("TS-2026-999999", numero.valor());
  }

  @Test
  void construirConValorNuloSeRechaza() {
    assertThrows(NumeroPedidoInvalidoException.class, () -> new NumeroPedido(null));
  }

  @Test
  void construirConFormatoInvalidoSeRechaza() {
    assertThrows(NumeroPedidoInvalidoException.class, () -> new NumeroPedido("TS-26-123"));
  }
}
