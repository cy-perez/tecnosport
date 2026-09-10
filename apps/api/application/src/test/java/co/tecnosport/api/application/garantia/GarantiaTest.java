package co.tecnosport.api.application.garantia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.garantia.EstadoReclamacionGarantia;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.domain.garantia.TerminosDeGarantia;
import co.tecnosport.api.domain.garantia.VigenciaGarantia;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GarantiaTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  private static final Instant RECLAMO =
      ZonedDateTime.of(2026, 6, 10, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static final TerminosDeGarantia TERMINOS =
      TerminosDeGarantia.de(12, Map.of(), Set.of("celulares"));

  private final RepositorioReclamacionesGarantiaFalso reclamaciones =
      new RepositorioReclamacionesGarantiaFalso();
  private final RepositorioPedidosFalsoGarantia pedidos = new RepositorioPedidosFalsoGarantia();
  private final RepositorioProductosParaGarantiaFalso productos =
      new RepositorioProductosParaGarantiaFalso();
  private final RepositorioSolicitudesAtencionFalso solicitudes =
      new RepositorioSolicitudesAtencionFalso();
  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private UUID varianteId;

  private Pedido sembrarPedido(String categoriaSlug) {
    varianteId = UUID.randomUUID();
    LineaPedido linea = PedidosDePrueba.linea(varianteId, UUID.randomUUID());
    Pedido pedido = PedidosDePrueba.entregado(MetodoPago.NEQUI, linea, ENTREGA);
    pedidos.sembrar(pedido);
    if (categoriaSlug != null) {
      productos.conProducto(
          varianteId,
          Producto.crear(
              "Camiseta running Dry-Fit",
              new Slug("camiseta-running"),
              "Descripcion",
              Marca.crear("TecnoSport"),
              new Categoria(
                  UUID.randomUUID(),
                  categoriaSlug,
                  new Slug(categoriaSlug),
                  LineaCatalogo.ROPA_Y_CALZADO)));
    }
    return pedido;
  }

  private RadicarReclamacionGarantia radicador(Instant ahora) {
    return new RadicarReclamacionGarantia(
        reclamaciones,
        pedidos,
        productos,
        new RadicarSolicitud(solicitudes, correos, new RelojFalso(ahora)),
        TERMINOS,
        new RelojFalso(ahora));
  }

  private ResolverGarantia resolvedor(Instant ahora) {
    return new ResolverGarantia(
        reclamaciones,
        solicitudes,
        pedidos,
        reintegros,
        new TopeDeReintegro(reintegros),
        new ResponderSolicitud(solicitudes, new RelojFalso(ahora)),
        new RelojFalso(ahora));
  }

  private ReclamacionGarantia radicar(String categoriaSlug) {
    Pedido pedido = sembrarPedido(categoriaSlug);
    return radicador(RECLAMO)
        .ejecutar(
            new RadicarReclamacionGarantiaComando(
                pedido.id(), varianteId, RECLAMO, "La costura se abrio", "admin:1"));
  }

  /**
   * Radicar una garantia radica tambien su solicitud de atencion. Sin eso, la reclamacion no
   * tendria numero que citar ni plazo de respuesta corriendo, que es el agujero que el bloque de
   * atencion vino a cerrar.
   */
  @Test
  void radicarUnaGarantiaRadicaTambienSuSolicitudDeAtencion() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");

    SolicitudAtencion solicitud = solicitudes.buscarPorId(reclamacion.solicitudId()).orElseThrow();
    assertEquals(TipoSolicitud.GARANTIA, solicitud.tipo());
    assertEquals(EstadoSolicitudAtencion.RADICADA, solicitud.estado());
    assertTrue(
        solicitud.numeroRadicado().valor().startsWith("TS-PQR-"),
        "la reclamacion llega con numero que el comprador puede citar");
    assertEquals(1, correos.enviados().size(), "y con su acuse");
  }

  @Test
  void elTerminoSaleDeLaCategoriaYNoDeUnaConstante() {
    ReclamacionGarantia ropa = radicar("ropa-deportiva");

    assertEquals(12, ropa.mesesDeTermino().orElseThrow());
    assertEquals(VigenciaGarantia.CUBIERTA, ropa.vigencia());
  }

  /**
   * Los terminos publicados dicen que para celulares aplica la garantia del fabricante, y ese plazo
   * sigue pendiente. Caer al termino general seria inventarlo: el sistema responde INDETERMINADA y
   * decide una persona.
   */
  @Test
  void unCelularNoCaeAlTerminoGeneralMientrasElDatoSigaPendiente() {
    ReclamacionGarantia celular = radicar("celulares");

    assertTrue(celular.mesesDeTermino().isEmpty());
    assertEquals(VigenciaGarantia.INDETERMINADA, celular.vigencia());
  }

  @Test
  void reclamarLaGarantiaDeAlgoQueNoSeComproEnEsePedidoFalla() {
    Pedido pedido = sembrarPedido("ropa-deportiva");

    assertThrows(
        LineaNoEsDelPedidoException.class,
        () ->
            radicador(RECLAMO)
                .ejecutar(
                    new RadicarReclamacionGarantiaComando(
                        pedido.id(), UUID.randomUUID(), RECLAMO, "algo", "admin:1")));
    assertTrue(reclamaciones.guardadas().isEmpty());
  }

  /**
   * Resolver la garantia responde la solicitud: si no, quedaria resuelta con su plazo de respuesta
   * corriendo para siempre en la bandeja, que es lo unico que no puede pasar con un plazo legal.
   */
  @Test
  void resolverCierraTambienLaSolicitudDeAtencion() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");

    resolvedor(RECLAMO.plusSeconds(86_400))
        .ejecutar(
            new ResolverGarantiaComando(
                reclamacion.id(),
                DesenlaceGarantia.REPARACION,
                "Se reparo la costura y se despacho de vuelta",
                null,
                null,
                null,
                "admin:1"));

    assertEquals(EstadoReclamacionGarantia.RESUELTA, reclamacion.estado());
    assertEquals(
        EstadoSolicitudAtencion.RESPONDIDA,
        solicitudes.buscarPorId(reclamacion.solicitudId()).orElseThrow().estado());
  }

  @Test
  void reponerNoDejaConstanciaDeDineroPorqueNoSaleDinero() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");

    resolvedor(RECLAMO.plusSeconds(86_400))
        .ejecutar(
            new ResolverGarantiaComando(
                reclamacion.id(),
                DesenlaceGarantia.REPOSICION,
                "Se repuso por una talla equivalente",
                null,
                null,
                null,
                "admin:1"));

    assertEquals(DesenlaceGarantia.REPOSICION, reclamacion.desenlace().orElseThrow());
    assertTrue(reintegros.guardados().isEmpty());
  }

  /**
   * Elegir la salida que devuelve el dinero y no decir cuánto ni por dónde es un cuerpo incompleto,
   * no un error de sistema. Antes de la guarda, el monto en nulo viajaba hasta el constructor de
   * {@code Dinero} y salía un 500 con "ocurrió un error inesperado" — quien atiende no tenía forma
   * de saber qué le faltaba. {@code CancelarPedido} tenía esta guarda desde el principio; garantía
   * y reversión no la heredaron.
   */
  @Test
  void resolverConReintegroSinMontoNiMedioPideLosDatosEnVezDeReventar() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");

    assertThrows(
        ReintegroRequeridoException.class, () -> resolverConReintegro(reclamacion, null, null));
    // Y con la mitad de los datos tampoco: una constancia sin medio no demuestra por dónde salió.
    assertThrows(
        ReintegroRequeridoException.class,
        () -> resolverConReintegro(reclamacion, BigDecimal.valueOf(50_000), null));

    assertTrue(reintegros.guardados().isEmpty(), "no queda constancia de un reintegro sin datos");
    assertTrue(reclamacion.desenlace().isEmpty(), "la reclamacion sigue abierta");
  }

  private void resolverConReintegro(
      ReclamacionGarantia reclamacion, BigDecimal monto, MedioReintegro medio) {
    resolvedor(RECLAMO.plusSeconds(86_400))
        .ejecutar(
            new ResolverGarantiaComando(
                reclamacion.id(),
                DesenlaceGarantia.REINTEGRO,
                "Se devolvio el dinero",
                monto,
                medio,
                null,
                "admin:1"));
  }

  /** La tercera salida deja la misma constancia que los otros cuatro caminos, con su motivo. */
  @Test
  void devolverElDineroDejaUnReintegroConMotivoGarantia() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");

    resolvedor(RECLAMO.plusSeconds(86_400))
        .ejecutar(
            new ResolverGarantiaComando(
                reclamacion.id(),
                DesenlaceGarantia.REINTEGRO,
                "No habia repuesto, se devolvio el dinero",
                BigDecimal.valueOf(50_000),
                MedioReintegro.TRANSFERENCIA_BANCARIA,
                "TRF-77",
                "admin:1"));

    assertEquals(1, reintegros.guardados().size());
    Reintegro reintegro = reintegros.guardados().get(0);
    assertEquals(MotivoReintegro.GARANTIA, reintegro.motivo());
    assertEquals(reclamacion.id(), reintegro.origenId());
    assertEquals(reintegro.id(), reclamacion.reintegroId().orElseThrow());
  }

  /**
   * El recorrido cruzado que estaba abierto: el pedido ya se devolvio completo por otro camino
   * —aqui un retracto, que deja su constancia con motivo RETRACTO— y despues alguien resuelve una
   * garantia del mismo pedido devolviendo el dinero otra vez. Los dos montos eran validos por
   * separado, porque cada camino solo se comparaba con el total del pedido; sumados, devolvian el
   * doble de lo que entro.
   *
   * <p>Radicar la garantia sigue siendo posible a proposito: puede haber razones para dejar
   * constancia, y prohibirlo seria una regla de negocio que nadie decidio. Lo que no puede es
   * pagarse dos veces.
   */
  @Test
  void unaGarantiaNoDevuelveLoQueOtroCaminoYaDevolvio() {
    ReclamacionGarantia reclamacion = radicar("ropa-deportiva");
    reintegros.guardar(
        Reintegro.registrar(
            reclamacion.pedidoId(),
            MotivoReintegro.RETRACTO,
            UUID.randomUUID(),
            Dinero.deCop(BigDecimal.valueOf(50_000)),
            MedioReintegro.TRANSFERENCIA_BANCARIA,
            "TRF-1",
            ENTREGA,
            "admin:1"));

    assertThrows(
        MontoDeReintegroInvalidoException.class,
        () ->
            resolvedor(RECLAMO.plusSeconds(86_400))
                .ejecutar(
                    new ResolverGarantiaComando(
                        reclamacion.id(),
                        DesenlaceGarantia.REINTEGRO,
                        "Se devolvio el dinero otra vez",
                        BigDecimal.valueOf(50_000),
                        MedioReintegro.TRANSFERENCIA_BANCARIA,
                        "TRF-88",
                        "admin:1")));

    // Ni segunda constancia ni reclamacion resuelta: la transaccion del controlador revierte, pero
    // esta prueba comprueba que el caso de uso no llego a escribir nada.
    assertEquals(1, reintegros.guardados().size());
    assertEquals(MotivoReintegro.RETRACTO, reintegros.guardados().get(0).motivo());
    assertTrue(reclamacion.desenlace().isEmpty());
  }

  /**
   * Fuera de termino no bloquea: puede haber garantia del fabricante por detras o una decision
   * comercial. Lo que hace falta es que la vigencia quede escrita para quien decide.
   */
  @Test
  void unaGarantiaFueraDeTerminoSePuedeResolverIgual() {
    Instant muyTarde = ZonedDateTime.of(2028, 1, 1, 10, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
    Pedido pedido = sembrarPedido("ropa-deportiva");
    ReclamacionGarantia reclamacion =
        radicador(muyTarde)
            .ejecutar(
                new RadicarReclamacionGarantiaComando(
                    pedido.id(), varianteId, muyTarde, "Se despego la suela", "admin:1"));

    assertEquals(VigenciaGarantia.FUERA_DE_TERMINO, reclamacion.vigencia());
    resolvedor(muyTarde)
        .ejecutar(
            new ResolverGarantiaComando(
                reclamacion.id(),
                DesenlaceGarantia.REPARACION,
                "Se reparo por cortesia comercial",
                null,
                null,
                null,
                "admin:1"));
    assertEquals(EstadoReclamacionGarantia.RESUELTA, reclamacion.estado());
  }

  @Test
  void unaReclamacionInexistenteFalla() {
    assertThrows(
        ReclamacionGarantiaNoEncontradaException.class,
        () ->
            resolvedor(RECLAMO)
                .ejecutar(
                    new ResolverGarantiaComando(
                        UUID.randomUUID(),
                        DesenlaceGarantia.REPARACION,
                        "algo",
                        null,
                        null,
                        null,
                        "admin:1")));
  }
}
