package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El vigilante de la bandeja: que alguien se entere sin tener que acordarse de mirar.
 *
 * <p>Lo que se prueba son las dos cosas de las que depende que sirva y no moleste: que avise de lo
 * que lleva más del umbral, y que <strong>no</strong> vuelva a avisar de lo mismo en cada vuelta.
 * Un vigilante que repite el correo cada seis horas se filtra a la papelera en una semana, y
 * entonces no vigila nada.
 */
class AvisarRevisionPendienteTest {

  private static final Instant AHORA = Instant.parse("2026-09-17T16:00:00Z");
  private static final Duration UMBRAL = Duration.ofHours(24);

  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Circular 4 # 70-20", null);

  private RepositorioEnviosFalso envios;
  private RepositorioEmisionesFalso emisiones;
  private RepositorioAcusesFalso acuses;
  private RepositorioAvisosFalso avisos;
  private RepositorioPedidosFalso pedidos;
  private CorreosFalsos correos;
  private AvisarRevisionPendiente caso;

  @BeforeEach
  void preparar() {
    envios = new RepositorioEnviosFalso();
    emisiones = new RepositorioEmisionesFalso();
    acuses = new RepositorioAcusesFalso();
    avisos = new RepositorioAvisosFalso();
    pedidos = new RepositorioPedidosFalso();
    correos = new CorreosFalsos();
    caso =
        new AvisarRevisionPendiente(
            new ListarEnviosEnRevision(envios, emisiones, acuses, pedidos),
            avisos,
            correos,
            new TextosFalsos(),
            () -> AHORA,
            UMBRAL,
            new CorreoElectronico("contacto@tecnosport.co"),
            50);
  }

  private Pedido sembrarPedido(int secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(89_900),
                    new BigDecimal("0.19"),
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA.minus(Duration.ofDays(10)));
    pedido.transicionar(
        EstadoPedido.PAGADO, "webhook", "pago aprobado", AHORA.minus(Duration.ofDays(9)));
    pedido.transicionar(
        EstadoPedido.EN_PREPARACION, "admin", "alistado", AHORA.minus(Duration.ofDays(9)));
    pedido.transicionar(
        EstadoPedido.DESPACHADO, "admin", "despachado", AHORA.minus(Duration.ofDays(9)));
    pedidos.guardar(pedido);
    return pedido;
  }

  private Envio sembrarGuiaQuieta(int secuencial, String numeroGuia, Instant recibidoEn) {
    Pedido pedido = sembrarPedido(secuencial);
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", numeroGuia, Dinero.deCop(8_200))),
            AHORA.minus(Duration.ofDays(9)));
    envio.registrarEvento(
        numeroGuia,
        new EventoSeguimiento(
            GeneradorIdentificador.nuevo(),
            EstadoEnvio.RETENIDO,
            "retenido en bodega",
            recibidoEn.minusSeconds(60),
            recibidoEn,
            "ev-" + numeroGuia));
    envios.guardar(envio);
    return envio;
  }

  @Test
  void avisa_de_lo_que_lleva_mas_del_umbral() {
    sembrarGuiaQuieta(1, "SE-1", AHORA.minus(Duration.ofHours(30)));

    ResultadoVigilanciaRevision resultado = caso.ejecutar();

    assertEquals(1, resultado.vencidas());
    assertEquals(1, resultado.avisadas());
    assertEquals(1, correos.enviados());
    assertEquals("contacto@tecnosport.co", correos.destinatarios.get(0));
  }

  /** Lo que acaba de entrar a la bandeja no es un descuido todavía. */
  @Test
  void no_avisa_de_lo_que_acaba_de_entrar() {
    sembrarGuiaQuieta(2, "SE-2", AHORA.minus(Duration.ofHours(2)));

    ResultadoVigilanciaRevision resultado = caso.ejecutar();

    assertEquals(0, resultado.vencidas());
    assertEquals(0, correos.enviados());
  }

  /**
   * La regla que decide si el vigilante sirve o se va a la papelera: la segunda vuelta no repite el
   * correo. La tarea corre cada seis horas contra un umbral de veinticuatro, así que sin esto el
   * mismo paquete generaría cuatro correos al día.
   */
  @Test
  void no_repite_el_aviso_en_la_vuelta_siguiente() {
    sembrarGuiaQuieta(3, "SE-3", AHORA.minus(Duration.ofHours(30)));

    caso.ejecutar();
    ResultadoVigilanciaRevision segunda = caso.ejecutar();

    assertEquals(1, segunda.vencidas());
    assertEquals(0, segunda.avisadas());
    assertEquals(1, correos.enviados());
  }

  /** Un correo con todo, y no uno por fila: diez correos seguidos se leen igual que ninguno. */
  @Test
  void manda_un_solo_correo_con_todo_lo_que_hay() {
    sembrarGuiaQuieta(4, "SE-4", AHORA.minus(Duration.ofHours(30)));
    sembrarGuiaQuieta(5, "SE-5", AHORA.minus(Duration.ofHours(40)));
    Pedido conEmision = sembrarPedido(6);
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(
            conEmision.id(),
            "Coordinadora",
            "tarifa-1",
            "admin:7",
            AHORA.minus(Duration.ofHours(30)));
    emision.indeterminada("la llamada no terminó", AHORA.minus(Duration.ofHours(30)));
    emisiones.guardar(emision);

    ResultadoVigilanciaRevision resultado = caso.ejecutar();

    assertEquals(3, resultado.vencidas());
    assertEquals(3, resultado.avisadas());
    assertEquals(1, correos.enviados());
    assertTrue(correos.cuerpos.get(0).contains("SE-4"));
    assertTrue(correos.cuerpos.get(0).contains("SE-5"));
    assertTrue(correos.cuerpos.get(0).contains("tarifa-1"));
  }

  /**
   * Avisar no es revisar: lo que está quieto sigue en la bandeja después del correo. Si el aviso se
   * guardara como un acuse, la fila desaparecería sin que nadie la hubiera mirado, que es el
   * defecto que toda esta parte vino a corregir.
   */
  @Test
  void avisar_no_saca_nada_de_la_bandeja() {
    sembrarGuiaQuieta(7, "SE-7", AHORA.minus(Duration.ofHours(30)));

    caso.ejecutar();

    assertEquals(
        1,
        new ListarEnviosEnRevision(envios, emisiones, acuses, pedidos).ejecutar(50).guias().size());
    assertTrue(acuses.guardados().isEmpty());
  }

  /** Lo que alguien ya revisó no está en la bandeja, así que tampoco genera un aviso. */
  @Test
  void lo_ya_revisado_no_genera_aviso() {
    sembrarGuiaQuieta(8, "SE-8", AHORA.minus(Duration.ofHours(30)));
    new AcusarRevisionDeGuia(envios, acuses, () -> AHORA)
        .ejecutar(new AcusarRevisionDeGuiaComando("SE-8", "admin:7", null));

    ResultadoVigilanciaRevision resultado = caso.ejecutar();

    assertEquals(0, resultado.vencidas());
    assertEquals(0, correos.enviados());
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CorreosFalsos implements EnviadorDeCorreo {

    private final List<String> destinatarios = new ArrayList<>();
    private final List<String> cuerpos = new ArrayList<>();

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpo) {
      destinatarios.add(destinatario.valor());
      cuerpos.add(cuerpo);
    }

    int enviados() {
      return destinatarios.size();
    }
  }

  /**
   * Devuelve la clave con sus argumentos pegados, que es lo que deja comprobar que cada fila llevó
   * sus datos sin acoplar la prueba a la redacción del correo.
   */
  private static final class TextosFalsos implements TextosDeCorreo {

    @Override
    public String dinero(co.tecnosport.api.domain.compartido.Dinero valor) {
      return valor.valor().toPlainString();
    }

    @Override
    public String texto(TextoDeCorreo texto, Object... argumentos) {
      StringBuilder sb = new StringBuilder(texto.clave());
      for (Object argumento : argumentos) {
        sb.append('|').append(argumento);
      }
      return sb.toString();
    }
  }
}
