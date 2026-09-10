package co.tecnosport.api.application.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.PlazoDeRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarReintegroTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  private static final Instant REINTEGRO =
      ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private final RepositorioSolicitudesRetractoFalso solicitudes =
      new RepositorioSolicitudesRetractoFalso();
  private final RepositorioPedidosParaRetractoFalso pedidos =
      new RepositorioPedidosParaRetractoFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();

  private RegistrarReintegro casoDeUso() {
    return new RegistrarReintegro(
        solicitudes,
        pedidos,
        reintegros,
        new TopeDeReintegro(reintegros),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(REINTEGRO));
  }

  private Reintegro elReintegroDe(SolicitudRetracto solicitud) {
    return reintegros.buscarPorId(solicitud.reintegroId().orElseThrow()).orElseThrow();
  }

  /** El pedido de prueba vale una línea de 50.000. */
  private SolicitudRetracto conProductoRecibido() {
    SolicitudRetracto solicitud = radicada();
    solicitud.recibirProducto(ENTREGA.plusSeconds(86_400));
    solicitudes.guardar(solicitud);
    return solicitud;
  }

  private SolicitudRetracto radicada() {
    Pedido pedido =
        PedidosDePrueba.entregado(
            MetodoPago.NEQUI, PedidosDePrueba.linea(UUID.randomUUID(), UUID.randomUUID()), ENTREGA);
    pedidos.sembrar(pedido);
    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            pedido.id(),
            ENTREGA,
            ENTREGA.plusSeconds(3600),
            "admin:1",
            null,
            null,
            CalendarioHabil.sinFestivosCargados());
    solicitudes.guardar(solicitud);
    return solicitud;
  }

  /**
   * El caso corriente de la Ley 2439: el comprador dice por donde quiere el dinero cuando manda los
   * datos de la cuenta, o sea despues de radicar. Se anota al registrar el reintegro, y tiene que
   * anotarse **antes** de cerrar la solicitud — con el dinero ya devuelto el dominio no lo acepta.
   */
  @Test
  void anotaLaPreferenciaQueLlegoDespuesDeRadicar() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                null,
                "admin:1"));

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
    assertEquals(MedioReintegro.TRANSFERENCIA_BANCARIA, solicitud.medioPreferido().orElseThrow());
    assertTrue(solicitud.respetaLaPreferencia(elReintegroDe(solicitud).medio()));
  }

  /**
   * Devolver por un medio distinto del pedido **no se bloquea**: puede haber un motivo real, como
   * una cuenta que rebota, y quien decide es una persona. Lo que no puede pasar es que no quede
   * rastro — con la preferencia guardada y el medio en la constancia, el incumplimiento es
   * demostrable en las dos direcciones, que es justo lo que la ley exige poder probar.
   */
  @Test
  void devolverPorOtroMedioNoSeBloqueaPeroQuedaContrastable() {
    SolicitudRetracto solicitud = conProductoRecibido();
    solicitud.anotarMedioPreferido(MedioReintegro.TRANSFERENCIA_BANCARIA);

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.EFECTIVO,
                null,
                null,
                "admin:1"));

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
    assertEquals(MedioReintegro.EFECTIVO, elReintegroDe(solicitud).medio());
    assertFalse(solicitud.respetaLaPreferencia(elReintegroDe(solicitud).medio()));
  }

  /** Y lo que ya se pidio no se reescribe al pagar: seria borrar la constancia de lo pedido. */
  @Test
  void noSePuedeCambiarLaPreferenciaAlRegistrarElReintegro() {
    SolicitudRetracto solicitud = conProductoRecibido();
    solicitud.anotarMedioPreferido(MedioReintegro.TRANSFERENCIA_BANCARIA);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_000),
                        MedioReintegro.EFECTIVO,
                        MedioReintegro.EFECTIVO,
                        null,
                        "admin:1")));
  }

  @Test
  void dejaLaConstanciaYCierraLaSolicitud() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                null,
                "TRF-9912",
                "admin:1"));

    assertEquals(EstadoSolicitudRetracto.REEMBOLSADA, solicitud.estado());
    Reintegro reintegro = elReintegroDe(solicitud);
    assertEquals(BigDecimal.valueOf(50_000), reintegro.monto().valor());
    assertEquals(REINTEGRO, reintegro.registradoEn());
    assertEquals("TRF-9912", reintegro.comprobante().orElseThrow());
  }

  /**
   * La constancia sabe de qué obligación nació y a qué solicitud responde. Sin las dos cosas es
   * plata que salió sin explicación, y es lo único que sostiene la invariante ahora que el
   * reintegro vive fuera de la solicitud.
   */
  @Test
  void laConstanciaGuardaSuMotivoYLaSolicitudQueLaJustifica() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.WOMPI,
                null,
                null,
                "admin:1"));

    Reintegro reintegro = elReintegroDe(solicitud);
    assertEquals(MotivoReintegro.RETRACTO, reintegro.motivo());
    assertEquals(solicitud.id(), reintegro.origenId());
    assertEquals(solicitud.pedidoId(), reintegro.pedidoId());
  }

  @Test
  void noSeReembolsaAntesDeQueVuelvaElProducto() {
    SolicitudRetracto solicitud = radicada();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_000),
                        MedioReintegro.WOMPI,
                        null,
                        null,
                        "admin:1")));
    assertTrue(solicitud.reintegroId().isEmpty());
    assertTrue(reintegros.guardados().isEmpty(), "no queda constancia de un reintegro que no fue");
  }

  @Test
  void noSeDevuelveMasDeLoQueSePago() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        MontoDeReintegroInvalidoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.valueOf(50_001),
                        MedioReintegro.WOMPI,
                        null,
                        null,
                        "admin:1")));
    assertEquals(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO, solicitud.estado());
  }

  @Test
  void unReintegroParcialSeAdmite() {
    // Puede haber un descuento pactado o una línea de varias: la ley pone un techo, no un valor
    // exacto.
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(30_000),
                MedioReintegro.EFECTIVO,
                null,
                null,
                "admin:1"));

    assertEquals(BigDecimal.valueOf(30_000), elReintegroDe(solicitud).monto().valor());
  }

  @Test
  void unReintegroDeCeroNoEsUnReintegro() {
    SolicitudRetracto solicitud = conProductoRecibido();

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        solicitud.id(),
                        BigDecimal.ZERO,
                        MedioReintegro.WOMPI,
                        null,
                        null,
                        "admin:1")));
  }

  /**
   * Y no deja una segunda constancia por el camino. Lo para el tope y no la máquina de estados, y
   * conviene saber por qué: el primer reintegro se llevó el total, así que al segundo ya no le
   * queda nada por devolver y el tope dictamina antes de que la solicitud se toque. Hasta que el
   * tope existió esto moría en la máquina de estados; las dos guardas siguen puestas y ninguna
   * escribe. La segunda afirmación es la que importa: sin ella, una implementación que guardara
   * primero pasaría esta prueba escribiendo dos veces el mismo hecho.
   */
  @Test
  void noSeReintegraDosVeces() {
    SolicitudRetracto solicitud = conProductoRecibido();
    RegistrarReintegro caso = casoDeUso();
    RegistrarReintegroComando comando =
        new RegistrarReintegroComando(
            solicitud.id(),
            BigDecimal.valueOf(50_000),
            MedioReintegro.WOMPI,
            null,
            null,
            "admin:1");
    caso.ejecutar(comando);

    assertThrows(MontoDeReintegroInvalidoException.class, () -> caso.ejecutar(comando));
    assertEquals(1, reintegros.guardados().size(), "constancias del mismo hecho");
  }

  /**
   * Y cuando al tope sí le queda margen —el primer reintegro fue parcial—, el doble clic muere
   * donde siempre: en la máquina de estados de la solicitud, que es la que sabe que este trámite ya
   * se cerró. Sin esta prueba, esa guarda podría desaparecer sin que nada se quejara.
   */
  @Test
  void unSegundoReintegroSobreLaMismaSolicitudMuereEnLaMaquinaDeEstados() {
    SolicitudRetracto solicitud = conProductoRecibido();
    RegistrarReintegro caso = casoDeUso();
    RegistrarReintegroComando parcial =
        new RegistrarReintegroComando(
            solicitud.id(),
            BigDecimal.valueOf(20_000),
            MedioReintegro.WOMPI,
            null,
            null,
            "admin:1");
    caso.ejecutar(parcial);

    assertThrows(ExcepcionDeDominio.class, () -> caso.ejecutar(parcial));
    assertEquals(1, reintegros.guardados().size(), "constancias del mismo hecho");
  }

  /**
   * Lo que la máquina de estados de la solicitud no puede ver: este pedido ya devolvió su total por
   * <b>otro</b> camino. Esta solicitud de retracto está impecable —radicada, producto recibido,
   * nunca reembolsada— y aun así no queda un peso por devolver.
   *
   * <p>Antes del tope esto pasaba: el retracto comparaba 50.000 contra el total del pedido, 50.000,
   * y lo dejaba pasar sin mirar la constancia de la garantía.
   */
  @Test
  void unRetractoNoDevuelveLoQueLaGarantiaYaDevolvio() {
    SolicitudRetracto solicitud = conProductoRecibido();
    reintegros.guardar(
        Reintegro.registrar(
            solicitud.pedidoId(),
            MotivoReintegro.GARANTIA,
            UUID.randomUUID(),
            Dinero.deCop(BigDecimal.valueOf(50_000)),
            MedioReintegro.TRANSFERENCIA_BANCARIA,
            "TRF-1",
            ENTREGA,
            "admin:1"));

    MontoDeReintegroInvalidoException error =
        assertThrows(
            MontoDeReintegroInvalidoException.class,
            () ->
                casoDeUso()
                    .ejecutar(
                        new RegistrarReintegroComando(
                            solicitud.id(),
                            BigDecimal.valueOf(50_000),
                            MedioReintegro.WOMPI,
                            null,
                            null,
                            "admin:1")));

    assertTrue(error.getMessage().contains("ya se devolvieron 50000"), error.getMessage());
    assertEquals(1, reintegros.guardados().size(), "constancias del pedido");
    // La solicitud sigue abierta: nada se guardó, así que el reintegro se puede registrar por el
    // monto que de verdad quede, si queda alguno.
    assertEquals(
        EstadoSolicitudRetracto.PRODUCTO_RECIBIDO,
        solicitudes.buscarPorId(solicitud.id()).orElseThrow().estado());
  }

  @Test
  void unaSolicitudInexistenteFalla() {
    assertThrows(
        SolicitudRetractoNoEncontradaException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new RegistrarReintegroComando(
                        UUID.randomUUID(),
                        BigDecimal.valueOf(1000),
                        MedioReintegro.WOMPI,
                        null,
                        null,
                        "admin:1")));
  }

  @Test
  void avisaAlCompradorDeQueElDineroSalio() {
    SolicitudRetracto solicitud = conProductoRecibido();

    casoDeUso()
        .ejecutar(
            new RegistrarReintegroComando(
                solicitud.id(),
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                null,
                null,
                "admin:1"));

    assertEquals(1, correos.enviados().size());
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("50000"));
  }
}
