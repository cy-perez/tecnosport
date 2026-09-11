package co.tecnosport.api.application.reversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.application.compartido.RepositorioSolicitudesReversionFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reversion.CausalReversion;
import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import co.tecnosport.api.domain.reversion.EstadoSolicitudReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReversionTest {

  /** Jueves. */
  private static final Instant HECHO =
      ZonedDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static final CalendarioHabil SIN_FESTIVOS = CalendarioHabil.sinFestivosCargados();

  private final RepositorioSolicitudesReversionFalso reversiones =
      new RepositorioSolicitudesReversionFalso();
  private final RepositorioPedidosFalsoReversion pedidos = new RepositorioPedidosFalsoReversion();
  private final RepositorioSolicitudesAtencionFalso solicitudes =
      new RepositorioSolicitudesAtencionFalso();
  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  /**
   * Un pedido despachado y nada mas: ni entregado, ni retractado. La reversion procede por no
   * entrega, y exigir un recorrido previo seria confundirla con el retracto.
   */
  private Pedido pedidoDespachado() {
    Pedido pedido =
        PedidosDePrueba.despachado(
            MetodoPago.NEQUI,
            PedidosDePrueba.linea(UUID.randomUUID(), UUID.randomUUID()),
            HECHO.minusSeconds(864_000));
    pedidos.sembrar(pedido);
    return pedido;
  }

  private RadicarReversion radicador(Instant ahora) {
    return new RadicarReversion(
        reversiones,
        pedidos,
        new RadicarSolicitud(
            solicitudes, correos, new TextosDeCorreoFalso(), new RelojFalso(ahora)),
        SIN_FESTIVOS,
        new RelojFalso(ahora));
  }

  private ResolverReversion resolvedor(Instant ahora) {
    return new ResolverReversion(
        reversiones,
        pedidos,
        reintegros,
        new TopeDeReintegro(reintegros, reversiones),
        new ResponderSolicitud(solicitudes, new RelojFalso(ahora)),
        new RelojFalso(ahora));
  }

  private SolicitudReversion radicar(CausalReversion causal, Instant ahora) {
    Pedido pedido = pedidoDespachado();
    return radicador(ahora)
        .ejecutar(
            new RadicarReversionComando(
                pedido.id(), causal, HECHO, ahora, "No llego nunca", "admin:1"));
  }

  /**
   * La prueba que sostiene la separacion entre las dos figuras: este pedido no paso por ningun
   * retracto —esta despachado, ni siquiera entregado— y aun asi admite reversion. Si el modelo las
   * hubiera confundido, este caso no existiria.
   */
  @Test
  void unPedidoLlegaAReversionSinPasarPorRetracto() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_NO_ENTREGADO, HECHO);

    assertEquals(EstadoSolicitudReversion.RADICADA, reversion.estado());
    assertEquals(
        TipoSolicitud.REVERSION,
        solicitudes.buscarPorId(reversion.solicitudId()).orElseThrow().tipo());
  }

  /** La causal queda guardada: decide a quien le toca responder y que prueba hace falta. */
  @Test
  void laCausalQuedaGuardada() {
    for (CausalReversion causal : CausalReversion.values()) {
      SolicitudReversion reversion = radicar(causal, HECHO);

      assertEquals(causal, reversion.causal());
    }
  }

  /**
   * Cinco dias habiles desde que tuvo noticia del hecho, no desde que compro ni desde que escribio.
   */
  @Test
  void dentroDeLosCincoDiasHabilesElVerdictoEsEnPlazo() {
    Instant alQuintoHabil =
        ZonedDateTime.of(2026, 9, 17, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    assertEquals(
        VerdictoPlazo.EN_PLAZO, radicar(CausalReversion.FRAUDE, alQuintoHabil).verdictoAlRadicar());
  }

  @Test
  void pasadoElPlazoSinFestivosCargadosElVerdictoEsIndeterminado() {
    Instant muyTarde = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    assertEquals(
        VerdictoPlazo.INDETERMINADO, radicar(CausalReversion.FRAUDE, muyTarde).verdictoAlRadicar());
  }

  /** Radicar fuera de plazo nunca se bloquea: decide una persona con el veredicto delante. */
  @Test
  void radicarFueraDePlazoSigueSiendoPosible() {
    Instant muyTarde = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    SolicitudReversion reversion = radicar(CausalReversion.FRAUDE, muyTarde);

    assertEquals(1, reversiones.guardadas().size());
    assertEquals(EstadoSolicitudReversion.RADICADA, reversion.estado());
  }

  /**
   * Los terminos prometen "nosotros facilitamos el tramite". Marcarlo sin decir que se hizo no
   * demuestra nada el dia que alguien lo discuta.
   */
  @Test
  void facilitarElTramiteExigeDejarEscritoQueSeHizo() {
    SolicitudReversion reversion = radicar(CausalReversion.FRAUDE, HECHO);
    RegistrarGestionReversion caso =
        new RegistrarGestionReversion(reversiones, new RelojFalso(HECHO));

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            caso.ejecutar(new RegistrarGestionReversionComando(reversion.id(), "   ", "admin:1")));
    assertEquals(EstadoSolicitudReversion.RADICADA, reversion.estado());

    caso.ejecutar(
        new RegistrarGestionReversionComando(
            reversion.id(), "Se radico ante Wompi con el soporte del comprador", "admin:1"));

    assertEquals(EstadoSolicitudReversion.GESTIONADA, reversion.estado());
    assertEquals(
        "Se radico ante Wompi con el soporte del comprador", reversion.gestion().orElseThrow());
  }

  /**
   * El emisor revirtio: el dinero volvio por la red de pagos y este sistema no movio un peso.
   * Inventarle una constancia seria registrar un pago que no hicimos.
   *
   * <p>Pero el monto si se anota, y eso es lo nuevo: sin el, ese dinero no contaba contra lo que el
   * pedido todavia puede devolver. Las dos afirmaciones juntas son el punto — no hay constancia y
   * si hay cifra.
   */
  @Test
  void siRevierteElEmisorNoQuedaConstanciaPeroSiQuedaLaCifra() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_NO_ENTREGADO, HECHO);

    resolvedor(HECHO.plusSeconds(86_400))
        .ejecutar(
            new ResolverReversionComando(
                reversion.id(),
                DesenlaceReversion.REVERTIDO_POR_EL_EMISOR,
                "El emisor confirmo la reversion",
                BigDecimal.valueOf(50_000),
                null,
                null,
                "admin:1"));

    assertEquals(EstadoSolicitudReversion.RESUELTA, reversion.estado());
    assertTrue(reversion.reintegroId().isEmpty());
    assertTrue(reintegros.guardados().isEmpty(), "no salio dinero de aqui");
    assertEquals(
        Dinero.deCop(BigDecimal.valueOf(50_000)),
        reversion.montoRevertidoPorElEmisor().orElseThrow(),
        "la cifra que revirtio el emisor queda anotada aunque no haya constancia");
  }

  /**
   * <b>El hallazgo.</b> El emisor revirtio el total y despues alguien intenta devolver otra vez por
   * otro camino. Antes pasaba: ese desenlace no deja {@code Reintegro}, asi que {@code yaDevuelto}
   * veia cero y el tope dejaba salir el total completo por segunda vez. Un contracargo es justo el
   * antecedente de un comprador insistente, o sea el caso probable y no el raro.
   */
  @Test
  void loQueRevirtioElEmisorConsumeElTopeDelPedido() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_NO_ENTREGADO, HECHO);
    resolvedor(HECHO.plusSeconds(86_400))
        .ejecutar(
            new ResolverReversionComando(
                reversion.id(),
                DesenlaceReversion.REVERTIDO_POR_EL_EMISOR,
                "El emisor confirmo la reversion",
                BigDecimal.valueOf(50_000),
                null,
                null,
                "admin:1"));

    TopeDeReintegro tope = new TopeDeReintegro(reintegros, reversiones);

    assertEquals(
        Dinero.deCop(BigDecimal.valueOf(50_000)),
        tope.yaDevuelto(reversion.pedidoId()),
        "lo que devolvio el emisor cuenta igual que lo que devolvimos nosotros");
    assertThrows(
        MontoDeReintegroInvalidoException.class,
        () ->
            tope.exigirQueQuepa(
                reversion.pedidoId(),
                Dinero.deCop(BigDecimal.valueOf(50_000)),
                Dinero.deCop(BigDecimal.valueOf(50_000))));
  }

  /**
   * Y no se puede resolver como reversion del emisor sin decir cuanto: seria dejar el tope ciego
   * otra vez, que es exactamente el defecto que esto cerro.
   */
  @Test
  void unaReversionDelEmisorSinMontoNoSeResuelve() {
    SolicitudReversion reversion = radicar(CausalReversion.FRAUDE, HECHO);

    assertThrows(
        ReintegroRequeridoException.class,
        () ->
            resolvedor(HECHO.plusSeconds(86_400))
                .ejecutar(
                    new ResolverReversionComando(
                        reversion.id(),
                        DesenlaceReversion.REVERTIDO_POR_EL_EMISOR,
                        "El emisor confirmo la reversion",
                        null,
                        null,
                        null,
                        "admin:1")));

    assertTrue(reversion.desenlace().isEmpty(), "la solicitud sigue abierta");
  }

  /**
   * Lo mismo que en la garantía, y por el mismo motivo: el único desenlace en que el dinero sale de
   * aquí exige decir cuánto y por dónde. Sin la guarda era un 500.
   */
  @Test
  void reintegrarDirectamenteSinMontoNiMedioPideLosDatosEnVezDeReventar() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_NO_CORRESPONDE, HECHO);

    assertThrows(
        ReintegroRequeridoException.class,
        () ->
            resolvedor(HECHO.plusSeconds(86_400))
                .ejecutar(
                    new ResolverReversionComando(
                        reversion.id(),
                        DesenlaceReversion.REINTEGRADO_DIRECTAMENTE,
                        "Se devolvio el dinero",
                        null,
                        null,
                        null,
                        "admin:1")));

    assertTrue(reintegros.guardados().isEmpty(), "no queda constancia de un reintegro sin datos");
    assertTrue(reversion.desenlace().isEmpty(), "la solicitud sigue abierta");
  }

  @Test
  void siDevolvemosNosotrosQuedaLaConstanciaConMotivoReversion() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_NO_CORRESPONDE, HECHO);

    resolvedor(HECHO.plusSeconds(86_400))
        .ejecutar(
            new ResolverReversionComando(
                reversion.id(),
                DesenlaceReversion.REINTEGRADO_DIRECTAMENTE,
                "Se devolvio el dinero por transferencia",
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                "TRF-91",
                "admin:1"));

    assertEquals(1, reintegros.guardados().size());
    assertEquals(MotivoReintegro.REVERSION, reintegros.guardados().get(0).motivo());
    assertEquals(reintegros.guardados().get(0).id(), reversion.reintegroId().orElseThrow());
  }

  @Test
  void resolverCierraTambienLaSolicitudDeAtencion() {
    SolicitudReversion reversion = radicar(CausalReversion.PRODUCTO_DEFECTUOSO, HECHO);

    resolvedor(HECHO.plusSeconds(86_400))
        .ejecutar(
            new ResolverReversionComando(
                reversion.id(),
                DesenlaceReversion.RECHAZADA,
                "El emisor la rechazo por falta de soporte",
                null,
                null,
                null,
                "admin:1"));

    assertEquals(
        EstadoSolicitudAtencion.RESPONDIDA,
        solicitudes.buscarPorId(reversion.solicitudId()).orElseThrow().estado());
  }

  @Test
  void unaReversionInexistenteFalla() {
    assertThrows(
        SolicitudReversionNoEncontradaException.class,
        () ->
            resolvedor(HECHO)
                .ejecutar(
                    new ResolverReversionComando(
                        UUID.randomUUID(),
                        DesenlaceReversion.RECHAZADA,
                        "algo",
                        null,
                        null,
                        null,
                        "admin:1")));
  }
}
