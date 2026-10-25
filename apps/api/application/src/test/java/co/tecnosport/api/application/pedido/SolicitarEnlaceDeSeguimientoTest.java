package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR-0088. Lo que importa de este caso de uso no es lo que devuelve —no devuelve nada— sino a
 * quién le escribe: al correo del pedido, solo cuando el número y el correo coinciden, y no más de
 * tres veces por hora.
 */
class SolicitarEnlaceDeSeguimientoTest {

  private static final Instant AHORA = Instant.parse("2026-10-25T12:00:00Z");
  private static final String CORREO = "cliente@tecnosport.co";
  private static final String URL_ESTADO = "https://tecnosport.co/es/checkout/estado";

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();
  private final LimitadorContando limitador = new LimitadorContando();
  private final SolicitarEnlaceDeSeguimiento caso =
      new SolicitarEnlaceDeSeguimiento(
          pedidos, correos, new TextosDeCorreoFalso(), limitador, () -> AHORA, URL_ESTADO);

  /**
   * Cuenta de verdad, a diferencia de {@code LimitadorDeIntentosFalso}: el tope es parte de lo que
   * se prueba.
   */
  private static final class LimitadorContando implements LimitadorDeIntentos {
    private final Map<String, Integer> conteos = new HashMap<>();
    int maximoVisto;
    Duration ventanaVista;

    @Override
    public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
      maximoVisto = maximoIntentos;
      ventanaVista = ventana;
      return conteos.merge(clave, 1, Integer::sum) <= maximoIntentos;
    }

    @Override
    public void olvidar(String clave) {
      conteos.remove(clave);
    }
  }

  private Pedido pedidoDePrueba(String correo, long secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            new CorreoElectronico(correo),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.00"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.CONTRAENTREGA,
            correo,
            AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private void solicitar(String numero, String correo) {
    caso.ejecutar(new SolicitarEnlaceDeSeguimientoComando(numero, correo));
  }

  @Test
  void conElNumeroYElCorreoCorrectosLeEscribeAlCorreoDelPedidoConSuEnlace() {
    Pedido pedido = pedidoDePrueba(CORREO, 1);

    solicitar("TS-2026-000001", CORREO);

    assertEquals(1, correos.enviados().size());
    EnviadorDeCorreoFalso.CorreoEnviado enviado = correos.enviados().getFirst();
    assertEquals(CORREO, enviado.destinatario().valor());
    assertEquals("[pedido.enlace_seguimiento.asunto|TS-2026-000001]", enviado.asunto());
    // El mismo enlace del comprobante y del despacho: entra por el id, no por el número.
    assertEquals(
        "[pedido.enlace_seguimiento.cuerpo|TS-2026-000001|"
            + URL_ESTADO
            + "?pedidoId="
            + pedido.id()
            + "#correo=cliente%40tecnosport.co]",
        enviado.cuerpoHtml());
  }

  /** Se copia y pega del correo: minúsculas y espacios alrededor, del número y del correo. */
  @Test
  void aceptaElNumeroEnMinusculaYConEspaciosYElCorreoEnMayuscula() {
    pedidoDePrueba(CORREO, 7);

    solicitar("  ts-2026-000007  ", "Cliente@TecnoSport.co");

    assertEquals(1, correos.enviados().size());
  }

  /**
   * <b>El ataque que esto cierra.</b> Quien conoce el correo de alguien y recorre números ya no ve
   * ningún pedido; lo más que consigue es que a esa persona le llegue su propio enlace.
   */
  @Test
  void conElCorreoEquivocadoNoLeEscribeANadie() {
    pedidoDePrueba(CORREO, 3);

    solicitar("TS-2026-000003", "otro@correo.co");

    assertTrue(correos.enviados().isEmpty());
  }

  /**
   * Se busca con los dos criterios a la vez: el número de otro pedido con el correo propio no abre
   * nada, ni le escribe al dueño de ese otro pedido.
   */
  @Test
  void elNumeroDeOtroPedidoNoLeEscribeASuDueno() {
    pedidoDePrueba(CORREO, 5);
    pedidoDePrueba("vecino@tecnosport.co", 6);

    solicitar("TS-2026-000006", CORREO);

    assertTrue(correos.enviados().isEmpty());
  }

  /** Ninguno lanza: si uno respondiera distinto, sería el oráculo que el formulario ya no da. */
  @Test
  void numeroInexistenteMalEscritoONuloYCorreoImposibleNoLanzanNiEscriben() {
    pedidoDePrueba(CORREO, 4);

    solicitar("TS-2026-999999", CORREO);
    solicitar("hola", CORREO);
    solicitar(null, CORREO);
    solicitar("TS-2026-000004", "no-es-un-correo");
    solicitar("TS-2026-000004", null);

    assertTrue(correos.enviados().isEmpty());
  }

  /** Decidido el 25 de octubre de 2026: tres por hora para cada pedido. */
  @Test
  void noLeEscribeMasDeTresVecesPorHoraAlMismoPedido() {
    pedidoDePrueba(CORREO, 8);

    for (int i = 0; i < 5; i++) {
      solicitar("TS-2026-000008", CORREO);
    }

    assertEquals(3, correos.enviados().size());
    assertEquals(3, limitador.maximoVisto);
    assertEquals(Duration.ofHours(1), limitador.ventanaVista);
  }

  /** El tope es por pedido: agotar el de uno no le quita el enlace al dueño de otro. */
  @Test
  void elTopeDeUnPedidoNoAlcanzaAOtro() {
    pedidoDePrueba(CORREO, 9);
    pedidoDePrueba(CORREO, 10);

    for (int i = 0; i < 4; i++) {
      solicitar("TS-2026-000009", CORREO);
    }
    solicitar("TS-2026-000010", CORREO);

    assertEquals(4, correos.enviados().size());
  }

  /** Un intento que no coincide no gasta el tope del titular. */
  @Test
  void losIntentosFallidosNoGastanElTopeDelPedido() {
    pedidoDePrueba(CORREO, 11);

    for (int i = 0; i < 5; i++) {
      solicitar("TS-2026-000011", "otro@correo.co");
    }
    solicitar("TS-2026-000011", CORREO);

    assertEquals(1, correos.enviados().size());
  }
}
