package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarSeguimientoPorNumeroTest {

  private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
  private static final String CORREO = "cliente@tecnosport.co";

  private RepositorioPedidosFalso pedidos;

  private ConsultarSeguimientoPorNumero crear() {
    pedidos = new RepositorioPedidosFalso();
    return new ConsultarSeguimientoPorNumero(pedidos);
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

  @Test
  void conElNumeroYElCorreoCorrectosDevuelveElPedido() {
    ConsultarSeguimientoPorNumero caso = crear();
    Pedido pedido = pedidoDePrueba(CORREO, 1);

    Pedido encontrado =
        caso.ejecutar(new ConsultarSeguimientoPorNumeroComando("TS-2026-000001", CORREO));

    assertEquals(pedido.id(), encontrado.id());
  }

  /** La gente copia y pega del correo, con espacios y a veces en minúscula. */
  @Test
  void elNumeroSeAceptaEnMinusculaYConEspacios() {
    ConsultarSeguimientoPorNumero caso = crear();
    Pedido pedido = pedidoDePrueba(CORREO, 7);

    Pedido encontrado =
        caso.ejecutar(new ConsultarSeguimientoPorNumeroComando("  ts-2026-000007  ", CORREO));

    assertEquals(pedido.id(), encontrado.id());
  }

  @Test
  void elCorreoSeComparaSinImportarMayusculasNiEspacios() {
    ConsultarSeguimientoPorNumero caso = crear();
    Pedido pedido = pedidoDePrueba(CORREO, 2);

    Pedido encontrado =
        caso.ejecutar(
            new ConsultarSeguimientoPorNumeroComando(
                "TS-2026-000002", "  Cliente@TecnoSport.co  "));

    assertEquals(pedido.id(), encontrado.id());
  }

  /**
   * <b>El corazón de este caso de uso.</b> El número es secuencial y adivinable: lo único que
   * protege el pedido es el correo. Si el correo equivocado diera una respuesta distinta de la de
   * un número inexistente, recorrer números diría cuáles existen.
   */
  @Test
  void conElCorreoEquivocadoLanzaPedidoNoEncontrado() {
    ConsultarSeguimientoPorNumero caso = crear();
    pedidoDePrueba(CORREO, 3);

    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new ConsultarSeguimientoPorNumeroComando("TS-2026-000003", "otro@correo.co")));
  }

  /**
   * Y los tres caminos tienen que decir <b>lo mismo</b>, no solo lanzar lo mismo: el {@code detail}
   * de la respuesta publica el mensaje de la excepción, así que un texto distinto por rama sería el
   * oráculo que el párrafo de arriba evita.
   */
  @Test
  void losTresCaminosDicenExactamenteLoMismo() {
    ConsultarSeguimientoPorNumero caso = crear();
    pedidoDePrueba(CORREO, 4);

    String correoEquivocado =
        assertThrows(
                PedidoNoEncontradoException.class,
                () ->
                    caso.ejecutar(
                        new ConsultarSeguimientoPorNumeroComando(
                            "TS-2026-000004", "otro@correo.co")))
            .getMessage();
    String numeroInexistente =
        assertThrows(
                PedidoNoEncontradoException.class,
                () ->
                    caso.ejecutar(
                        new ConsultarSeguimientoPorNumeroComando("TS-2026-999999", CORREO)))
            .getMessage();
    String numeroMalEscrito =
        assertThrows(
                PedidoNoEncontradoException.class,
                () -> caso.ejecutar(new ConsultarSeguimientoPorNumeroComando("hola", CORREO)))
            .getMessage();

    assertEquals(correoEquivocado, numeroInexistente);
    assertEquals(correoEquivocado, numeroMalEscrito);
  }

  /**
   * Un número con el formato correcto pero de otro pedido tampoco sirve: se busca por número y se
   * compara el correo del pedido encontrado, no el del que se pidió.
   */
  @Test
  void elNumeroDeOtroPedidoNoAbreElPropio() {
    ConsultarSeguimientoPorNumero caso = crear();
    pedidoDePrueba(CORREO, 5);
    pedidoDePrueba("vecino@tecnosport.co", 6);

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ConsultarSeguimientoPorNumeroComando("TS-2026-000006", CORREO)));
  }

  @Test
  void sinNumeroLanzaPedidoNoEncontrado() {
    ConsultarSeguimientoPorNumero caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ConsultarSeguimientoPorNumeroComando(null, CORREO)));
  }
}
