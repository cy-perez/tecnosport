package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.CalculadorDePHashPorContenido;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.EsperaGuionada;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.ExtractorFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.FuenteFija;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.domain.proveedores.TopesDeGanancia;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El lote entero con puertos falsos: los nueve mensajes del criterio de aceptación —los cinco
 * primeros de bolsos y los cuatro primeros de ropa del anexo— con sus fotos.
 */
class ProcesarLoteDeIngestaTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");
  private static final String ARCHIVO = "proveedores/x/exportaciones/a.zip";
  private static final Pattern TONOS =
      Pattern.compile("(\\d+)\\s+(?:tonos|colores|combinaciones)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SIRVE_HASTA =
      Pattern.compile("Sirve hasta la (?:talla )?(\\w+)", Pattern.CASE_INSENSITIVE);

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioProductosEnMemoria productos = new RepositorioProductosEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final EnTransaccionPropiaFalsa transacciones = new EnTransaccionPropiaFalsa();

  private EsperaGuionada espera = new EsperaGuionada();
  private Proveedor proveedor;
  private LoteIngesta lote;

  @BeforeEach
  void unLoteEnLaCola() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    lote = LoteIngesta.recibirExportacion(proveedor.id(), ARCHIVO, T);
    lotes.guardar(lote);
  }

  private ProcesarLoteDeIngesta casoCon(FuenteFija fuente, ExtractorDeProductos extractor) {
    RelojFalso reloj = new RelojFalso(AHORA);
    RegistrarMensajesDeProveedor registrar =
        new RegistrarMensajesDeProveedor(
            proveedores, lotes, mensajes, almacen, new CalculadorDePHashPorContenido());
    ArmarPublicaciones armar =
        new ArmarPublicaciones(
            lotes,
            proveedores,
            mensajes,
            publicaciones,
            new AgrupadorDePublicaciones(Duration.ofMinutes(15)));
    ExtraerProductoDePublicacion extraer =
        new ExtraerProductoDePublicacion(extractor, new BigDecimal("0.75"));
    ResolverBorrador resolver =
        new ResolverBorrador(
            borradores,
            new RepositorioProductosDeProveedorEnMemoria(productos),
            productos,
            publicaciones,
            almacen,
            new CalculadorDePHashPorContenido(),
            reloj,
            Map.of(
                LineaCatalogo.BOLSOS,
                new BigDecimal("1.35"),
                LineaCatalogo.ROPA,
                new BigDecimal("1.30")),
            new TopesDeGanancia(Dinero.deCop(20000), Dinero.deCop(30000)),
            6);
    return new ProcesarLoteDeIngesta(
        lotes,
        proveedores,
        mensajes,
        publicaciones,
        fuente,
        registrar,
        armar,
        extraer,
        resolver,
        transacciones,
        espera,
        reloj);
  }

  /** Los 25 del anexo, y de ahí los nueve del criterio: bolsos 1-5 y ropa 1-4. */
  private static List<String> losNueveDelCriterio() {
    try (InputStream entrada =
        ProcesarLoteDeIngestaTest.class.getResourceAsStream("/proveedores/anexo-mensajes.txt")) {
      String todo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
      List<String> todos =
          Arrays.stream(todo.split("\n--- Fotos ---\n")).map(String::strip).toList();
      List<String> nueve = new ArrayList<>(todos.subList(0, 5));
      nueve.addAll(todos.subList(14, 18));
      return nueve;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Cada texto de producto seguido de dos fotos, diez minutos entre productos. */
  private static List<MensajeCrudo> comoChat(List<String> textos) {
    List<MensajeCrudo> crudos = new ArrayList<>();
    int i = 0;
    for (String texto : textos) {
      Instant momento = T.plusSeconds(600L * i);
      crudos.add(MensajeCrudo.texto(momento, ApoyoDeIngesta.REMITENTE, texto));
      crudos.add(
          MensajeCrudo.imagen(
              momento.plusSeconds(20),
              ApoyoDeIngesta.REMITENTE,
              null,
              ApoyoDeIngesta.foto("IMG-" + i + "-a.jpg")));
      crudos.add(
          MensajeCrudo.imagen(
              momento.plusSeconds(40),
              ApoyoDeIngesta.REMITENTE,
              null,
              ApoyoDeIngesta.foto("IMG-" + i + "-b.jpg")));
      i++;
    }
    crudos.add(MensajeCrudo.texto(T.plusSeconds(100_000), "Tecno Sport", "Recibido, gracias"));
    return crudos;
  }

  /** Un extractor determinista que hace lo que haría el modelo con estos textos. */
  private static ExtractorFalso extractorDelAnexo() {
    return ExtractorFalso.porTexto(
        texto -> {
          String completo = texto.completo();
          Matcher tonos = TONOS.matcher(completo);
          Matcher sirve = SIRVE_HASTA.matcher(completo);
          boolean ropa = completo.contains("TALLAS");
          return new ProductoExtraido(
              true,
              false,
              completo
                  .lines()
                  .map(String::strip)
                  .filter(l -> !l.isEmpty() && !l.contains("colección") && !l.contains("COLECCION"))
                  .findFirst()
                  .orElse("x")
                  .replace("*", ""),
              ropa ? LineaCatalogo.ROPA : LineaCatalogo.BOLSOS,
              ropa ? TipoProductoProveedor.CONJUNTO_PANTALON : TipoProductoProveedor.BOLSO,
              PatronDePrecio.extraer(completo).orElse(null),
              sirve.find() ? Tallas.unica(sirve.group(1)) : Tallas.desconocida(),
              tonos.find() ? Integer.parseInt(tonos.group(1)) : null,
              List.of(),
              null,
              null,
              null,
              false,
              new BigDecimal("0.9"),
              null);
        });
  }

  /** El primer criterio de aceptación: nueve publicaciones, nueve borradores, esos precios. */
  @Test
  void losNueveMensajesDelAnexoDejanNuevePublicacionesYNueveBorradores() {
    ProcesarLoteDeIngesta caso =
        casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractorDelAnexo());

    LoteIngesta resultado = caso.ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, resultado.estado());
    ResumenIngesta resumen = resultado.resumen().orElseThrow();
    assertEquals(28, resumen.mensajesLeidos());
    assertEquals(1, resumen.mensajesIgnorados());
    assertEquals(27, resumen.mensajesNuevos());
    assertEquals(9, resumen.publicaciones());
    assertEquals(9, resumen.borradoresNuevos());
    assertEquals(0, resumen.renovaciones());
    assertEquals(0, resumen.descartes());

    List<BorradorProducto> enRevision =
        borradores.porId.values().stream()
            .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
            .sorted((a, b) -> a.creadoEn().compareTo(b.creadoEn()))
            .toList();
    assertEquals(9, enRevision.size());
    List<PublicacionProveedor> pubs = publicaciones.listarDeLote(lote.id());
    List<Long> precios = new ArrayList<>();
    for (PublicacionProveedor pub : pubs) {
      BorradorProducto b =
          enRevision.stream()
              .filter(x -> x.publicacionId().equals(pub.id()))
              .findFirst()
              .orElseThrow();
      precios.add(b.precioProveedor().orElseThrow().valor().longValue());
    }
    assertEquals(
        List.of(53000L, 60000L, 62000L, 52000L, 60000L, 60000L, 45000L, 45000L, 45000L), precios);

    List<BorradorProducto> porPublicacion =
        pubs.stream()
            .map(
                pub ->
                    enRevision.stream()
                        .filter(x -> x.publicacionId().equals(pub.id()))
                        .findFirst()
                        .orElseThrow())
            .toList();
    List<Integer> tonos =
        porPublicacion.subList(0, 5).stream().map(b -> b.cantidadTonos().orElse(-1)).toList();
    assertEquals(List.of(4, 3, 2, 4, 3), tonos);
    List<String> sirveHasta =
        porPublicacion.subList(5, 9).stream()
            .map(b -> b.tallas().tipo() == TipoDeTalla.UNICA ? b.tallas().sirveHasta() : "?")
            .toList();
    assertEquals(List.of("L", "L", "XL", "XL"), sirveHasta);
    assertTrue(
        porPublicacion.stream().allMatch(b -> b.pHash().isPresent()),
        "cada uno con su huella visual");
    // 53.000 × 1,35 gana 18.600 y 60.000 × 1,30 gana 18.000: los dos suben a ganar 20.000.
    assertEquals(Optional.of(Dinero.deCop(73000)), porPublicacion.get(0).precioVentaSugerido());
    assertEquals(
        Optional.of(Dinero.deCop(80000)),
        porPublicacion.get(5).precioVentaSugerido(),
        "ropa al 1,30, con la ganancia mínima");
    assertTrue(pubs.stream().allMatch(p -> p.estado() == EstadoPublicacionProveedor.EXTRAIDA));
  }

  /** El segundo criterio: la misma exportación otra vez no crea nada. */
  @Test
  void volverASubirLaMismaExportacionNoCreaMensajesNiPublicacionesNiBorradores() {
    List<MensajeCrudo> chat = comoChat(losNueveDelCriterio());
    casoCon(new FuenteFija(chat), extractorDelAnexo()).ejecutar(lote.id());
    LoteIngesta segundo =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/b.zip", T);
    lotes.guardar(segundo);

    LoteIngesta resultado =
        casoCon(new FuenteFija(chat), extractorDelAnexo()).ejecutar(segundo.id());

    ResumenIngesta resumen = resultado.resumen().orElseThrow();
    assertEquals(28, resumen.mensajesLeidos());
    assertEquals(0, resumen.mensajesNuevos());
    assertEquals(0, resumen.publicaciones());
    assertEquals(0, resumen.borradoresNuevos());
    assertEquals(27, mensajes.guardados.size());
    assertEquals(9, borradores.porId.size());
  }

  /** Una publicación que el extractor no puede leer queda en ERROR y el lote termina igual. */
  @Test
  void unaExtraccionFallidaNoTumbaElLote() {
    List<String> textos = List.of("Bolso 💰 53.000", "Morral 💰 40.000");
    ExtractorFalso extractor =
        ExtractorFalso.porTexto(
            texto -> {
              if (texto.completo().contains("Morral")) {
                throw new ExtraccionFallidaException("La API no respondió tras 4 intentos.");
              }
              return new ProductoExtraido(
                  true,
                  false,
                  "Bolso",
                  LineaCatalogo.BOLSOS,
                  TipoProductoProveedor.BOLSO,
                  Dinero.deCop(53000),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  false,
                  BigDecimal.ONE,
                  null);
            });

    LoteIngesta resultado =
        casoCon(new FuenteFija(comoChat(textos)), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, resultado.estado());
    ResumenIngesta resumen = resultado.resumen().orElseThrow();
    assertEquals(2, resumen.publicaciones());
    assertEquals(1, resumen.borradoresNuevos());
    assertEquals(1, resumen.descartes());
    List<PublicacionProveedor> pubs = publicaciones.listarDeLote(lote.id());
    assertEquals(EstadoPublicacionProveedor.ERROR, pubs.get(1).estado());
    assertTrue(pubs.get(1).motivo().orElseThrow().contains("4 intentos"));
  }

  /** El error queda escrito en el lote para el panel, y se propaga para el log. */
  @Test
  void unArchivoIlegibleDejaElLoteEnErrorConElMotivoYPropaga() {
    ProcesarLoteDeIngesta caso =
        casoCon(
            new FuenteFija(new ExportacionIlegibleException("El zip no trae .txt.")),
            extractorDelAnexo());

    assertThrows(ExportacionIlegibleException.class, () -> caso.ejecutar(lote.id()));

    LoteIngesta guardado = lotes.buscarPorId(lote.id()).orElseThrow();
    assertEquals(EstadoLote.ERROR, guardado.estado());
    assertEquals(Optional.of("El zip no trae .txt."), guardado.detalleError());
  }

  @Test
  void unFalloInesperadoSeEscribeComoTalSinElMensajeCrudo() {
    ProcesarLoteDeIngesta caso =
        casoCon(
            new FuenteFija(new NullPointerException("Cannot invoke \"x.y()\"")),
            extractorDelAnexo());

    assertThrows(NullPointerException.class, () -> caso.ejecutar(lote.id()));

    String detalle = lotes.buscarPorId(lote.id()).orElseThrow().detalleError().orElseThrow();
    assertTrue(detalle.contains("Error inesperado"), detalle);
    assertTrue(!detalle.contains("Cannot invoke"), detalle);
  }

  @Test
  void unLoteQueYaNoEstaEnLaColaSeDevuelveSinTocar() {
    FuenteFija fuente = new FuenteFija(List.of());
    ProcesarLoteDeIngesta caso = casoCon(fuente, extractorDelAnexo());
    caso.ejecutar(lote.id());
    int actualizaciones = lotes.actualizaciones;

    LoteIngesta otraVez = caso.ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, otraVez.estado());
    assertEquals(actualizaciones, lotes.actualizaciones);
    assertEquals(1, fuente.lecturas, "el archivo se lee una sola vez");
  }

  @Test
  void sinLoteRevienta() {
    ProcesarLoteDeIngesta caso = casoCon(new FuenteFija(List.of()), extractorDelAnexo());

    assertThrows(LoteNoEncontradoException.class, () -> caso.ejecutar(UUID.randomUUID()));
  }

  /**
   * Violeta (2 de octubre de 2026): dos pies de foto con un conjunto cada uno, y el jean repetido
   * en los dos. Salen tres borradores de dos publicaciones —el jean una sola vez, porque su código
   * Q337 es el mismo—, todos con las fotos compartidas y sin huella visual.
   */
  @Test
  void dosConjuntosDeVioletaDejanTresBorradoresYElJeanUnaSolaVez() {
    String primero =
        """
        ✨NEW COLLECTION✨
        Chaleco Denim (Q300)
        💲99
        Talla S M L

        Jean wide Leg Licrado (Q337)
        💲124
        Talla S M L XL""";
    String segundo =
        """
        NEW NEW NEW✨✨
        Blusa Rib larga (VY2719)
        💲28
        Talla Única

        Jean wide Leg Licrado (Q337)
        💲124
        Talla S M L XL""";
    List<MensajeCrudo> crudos =
        List.of(
            MensajeCrudo.imagen(
                T, ApoyoDeIngesta.REMITENTE, primero, ApoyoDeIngesta.foto("IMG-0315.jpg")),
            MensajeCrudo.imagen(
                T.plusSeconds(5),
                ApoyoDeIngesta.REMITENTE,
                null,
                ApoyoDeIngesta.foto("IMG-0316.jpg")),
            MensajeCrudo.imagen(
                T.plusSeconds(1320),
                ApoyoDeIngesta.REMITENTE,
                segundo,
                ApoyoDeIngesta.foto("IMG-0318.jpg")));
    ExtractorFalso extractor =
        ExtractorFalso.variosPorTexto(
            texto ->
                texto.completo().contains("Chaleco")
                    ? List.of(
                        prenda("Chaleco Denim", 99000, "Q300"),
                        prenda("Jean wide Leg Licrado", 124000, "Q337"))
                    : List.of(
                        prenda("Blusa Rib larga", 28000, "VY2719"),
                        prenda("Jean wide Leg Licrado", 124000, "Q337")));

    LoteIngesta resultado = casoCon(new FuenteFija(crudos), extractor).ejecutar(lote.id());

    ResumenIngesta resumen = resultado.resumen().orElseThrow();
    assertEquals(2, resumen.publicaciones());
    assertEquals(3, resumen.borradoresNuevos());
    assertEquals(1, resumen.descartes(), "el jean repetido");
    List<BorradorProducto> enRevision =
        borradores.porId.values().stream()
            .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
            .toList();
    assertEquals(
        Set.of("Chaleco Denim", "Jean wide Leg Licrado", "Blusa Rib larga"),
        enRevision.stream().map(b -> b.titulo().orElseThrow()).collect(Collectors.toSet()));
    assertTrue(enRevision.stream().allMatch(b -> b.pHash().isEmpty()));
    assertTrue(
        enRevision.stream().allMatch(b -> b.alertas().contains(AlertaBorrador.FOTOS_COMPARTIDAS)));
    assertTrue(
        enRevision.stream()
            .noneMatch(b -> b.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE)),
        "cada precio con el de su posición");
    assertTrue(
        publicaciones.listarDeLote(lote.id()).stream()
            .allMatch(p -> p.estado() == EstadoPublicacionProveedor.EXTRAIDA));
  }

  private static ProductoExtraido prenda(String titulo, long precio) {
    return prenda(titulo, precio, null);
  }

  private static ProductoExtraido prenda(String titulo, long precio, String codigo) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.CHAQUETA,
        Dinero.deCop(precio),
        Tallas.desconocida(),
        null,
        List.of(),
        null,
        null,
        null,
        false,
        new BigDecimal("0.9"),
        null,
        codigo);
  }

  // --- Pausar y detener desde el panel ---

  /**
   * Lo que haría el panel, en el momento en que la prueba lo diga: leer, cambiar y guardar su
   * propia copia. Con {@code devolverCopias}, la que tiene el trabajador no se entera hasta que
   * relee, que es justo lo que hay que probar.
   */
  private void enElPanel(java.util.function.Consumer<LoteIngesta> accion) {
    LoteIngesta delPanel = lotes.buscarPorId(lote.id()).orElseThrow();
    accion.accept(delPanel);
    lotes.actualizar(delPanel);
  }

  /** Las pruebas de pausar y detener leen copias, como la base. */
  private void conCopias() {
    lotes.devolverCopias = true;
    lotes.guardar(lote);
  }

  /** Un extractor que, en la llamada n, hace algo en el panel antes de responder. */
  private ExtractorDeProductos extractorQueEnLaLlamada(int n, Runnable accion) {
    ExtractorFalso delAnexo = extractorDelAnexo();
    int[] llamadas = {0};
    return texto -> {
      if (++llamadas[0] == n) {
        accion.run();
      }
      return delAnexo.extraer(texto);
    };
  }

  /** La pausa retiene el hilo donde iba y, al reanudar, sigue sin repetir ni saltarse nada. */
  @Test
  void enPausaEsperaYAlReanudarTerminaEntero() {
    conCopias();
    espera.luego(() -> {}).luego(() -> enElPanel(LoteIngesta::reanudar));
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(3, () -> enElPanel(LoteIngesta::pausar));

    LoteIngesta resultado =
        casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, resultado.estado());
    assertEquals(2, espera.esperas, "esperó mientras siguió en pausa, no una sola vez");
    assertEquals(9, resultado.resumen().orElseThrow().borradoresNuevos());
    assertEquals(9, borradores.porId.size());
  }

  /**
   * Detener a la mitad suelta el hilo con lo que alcanzó: las publicaciones que faltan no se tocan.
   */
  @Test
  void detenerEnCursoCierraConLoQueAlcanzoYNoSigue() {
    conCopias();
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(3, () -> enElPanel(l -> l.pedirDetencion(AHORA)));

    LoteIngesta resultado =
        casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.DETENIDO, resultado.estado());
    ResumenIngesta resumen = resultado.resumen().orElseThrow();
    assertEquals(28, resumen.mensajesLeidos());
    assertEquals(9, resumen.publicaciones());
    assertEquals(3, resumen.borradoresNuevos(), "la que estaba en curso termina; las demás no");
    assertEquals(3, borradores.porId.size());
    assertEquals(Optional.of(AHORA), resultado.terminadoEn());
    assertEquals(0, espera.esperas);
  }

  @Test
  void detenerEnPausaSueltaElHiloSinVolverATrabajar() {
    conCopias();
    espera.luego(() -> enElPanel(l -> l.pedirDetencion(AHORA)));
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(1, () -> enElPanel(LoteIngesta::pausar));

    LoteIngesta resultado =
        casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.DETENIDO, resultado.estado());
    assertEquals(1, resultado.resumen().orElseThrow().borradoresNuevos());
  }

  /** El que se detuvo en la cola se salta: ni se lee el archivo. */
  @Test
  void elDetenidoEnLaColaSeSaltaSinLeerElArchivo() {
    conCopias();
    enElPanel(l -> l.pedirDetencion(AHORA));
    FuenteFija fuente = new FuenteFija(comoChat(losNueveDelCriterio()));

    LoteIngesta resultado = casoCon(fuente, extractorDelAnexo()).ejecutar(lote.id());

    assertEquals(EstadoLote.DETENIDO, resultado.estado());
    assertEquals(0, fuente.lecturas);
    assertTrue(mensajes.guardados.isEmpty());
  }

  /** Si la aplicación se apaga con el lote en pausa, el lote no se queda abierto para siempre. */
  @Test
  void unaPausaInterrumpidaDejaElLoteEnErrorConSuMotivo() {
    conCopias();
    espera.luego(
        () -> {
          throw new IngestaInterrumpidaException();
        });
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(1, () -> enElPanel(LoteIngesta::pausar));

    assertThrows(
        IngestaInterrumpidaException.class,
        () ->
            casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor)
                .ejecutar(lote.id()));

    LoteIngesta guardado = lotes.buscarPorId(lote.id()).orElseThrow();
    assertEquals(EstadoLote.ERROR, guardado.estado());
    assertTrue(guardado.detalleError().orElseThrow().contains("en pausa"));
  }

  /**
   * El final se escribe sobre el lote releído y bloqueado, no sobre el que se tomó al empezar: el
   * panel lo pausó después del último punto de control, y terminar sobre la copia vieja —que dice
   * PROCESANDO— no se habría enterado. Lo que importa es que el resultado salga del lote releído.
   */
  @Test
  void elFinalSeEscribeSobreElLoteBloqueado() {
    conCopias();
    List<String> uno = losNueveDelCriterio().subList(0, 1);
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(1, () -> enElPanel(LoteIngesta::pausar));
    // Pausado después del último punto de control: el trabajador no espera, termina.
    LoteIngesta resultado = casoCon(new FuenteFija(comoChat(uno)), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, resultado.estado());
    assertEquals(EstadoLote.TERMINADO, lotes.buscarPorId(lote.id()).orElseThrow().estado());
    assertEquals(2, lotes.bloqueos, "uno al tomarlo y otro al soltarlo");
  }

  /**
   * Otra instancia arrancó y cerró con error el lote que este hilo tenía en pausa
   * (ReanudarLotesDeIngesta): el hilo no sigue creando borradores ni escribe encima.
   */
  @Test
  void unLoteQueOtraInstanciaCerroNoSigueNiSeEscribe() {
    conCopias();
    espera.luego(() -> enElPanel(l -> l.fallar("reinicio", AHORA)));
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(1, () -> enElPanel(LoteIngesta::pausar));

    LoteIngesta resultado =
        casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor).ejecutar(lote.id());

    assertEquals(EstadoLote.ERROR, resultado.estado());
    assertEquals(Optional.of("reinicio"), resultado.detalleError());
    assertEquals(1, borradores.porId.size(), "la publicación en curso y ninguna más");
  }

  /** Y si además se eliminó, no se vuelve a insertar desde la copia que tenía el hilo. */
  @Test
  void unLoteEliminadoMientrasEsperabaNoSeReinserta() {
    conCopias();
    espera.luego(
        () -> {
          enElPanel(l -> l.fallar("reinicio", AHORA));
          lotes.eliminarConSuHistorial(lote.id());
        });
    ExtractorDeProductos extractor =
        extractorQueEnLaLlamada(1, () -> enElPanel(LoteIngesta::pausar));

    casoCon(new FuenteFija(comoChat(losNueveDelCriterio())), extractor).ejecutar(lote.id());

    assertEquals(Optional.empty(), lotes.buscarPorId(lote.id()));
  }

  private static final String POLO_PRADA =
      "*NUEVA POLO 1.1🍯*\n *MARCA P R A D A*\n*TELA FRIA*\n*PRECIO X DIFUSIÓN $50.000💰*";
  private static final String CHAT_DE_CABALLERO = "• M͟E͟R͟A͟K͟I͟ ͟M͟E͟N͟ • LC 1-228";
  private static final String CHAT_GENERAL = "MERAKI • FICUS 1C-14 & 1C-13 #COMUNIDAD";

  /**
   * La polo Prada con su foto, como la manda Meraki en cualquiera de sus dos chats. Cada chat la
   * manda a su hora: con la misma, el registro la tomaría por el mismo mensaje.
   */
  private static List<MensajeCrudo> poloPrada(String foto, Instant cuando) {
    return List.of(
        MensajeCrudo.imagen(cuando, ApoyoDeIngesta.REMITENTE, null, ApoyoDeIngesta.foto(foto)),
        MensajeCrudo.texto(cuando.plusSeconds(20), ApoyoDeIngesta.REMITENTE, POLO_PRADA));
  }

  /** Los dos ganchos de los repositorios en memoria, conectados como el adaptador real. */
  private void conLosChatsDeVerdad() {
    publicaciones.textosDeCaballero =
        proveedorId ->
            publicaciones.porId.values().stream()
                .filter(p -> p.proveedorId().equals(proveedorId))
                .filter(p -> lotes.buscarPorId(p.loteId()).orElseThrow().esChatDeCaballero())
                .map(p -> textoDe(p.mensajePrincipalId()))
                .toList();
    borradores.comoAnuncio =
        b -> {
          PublicacionProveedor p = publicaciones.buscarPorId(b.publicacionId()).orElseThrow();
          return new AnuncioEnRevision(
              b.id(),
              textoDe(p.mensajePrincipalId()),
              b.pHash().stream().toList(),
              lotes.buscarPorId(p.loteId()).orElseThrow().esChatDeCaballero());
        };
  }

  private String textoDe(UUID mensajeId) {
    return mensajes.guardados.stream()
        .filter(m -> m.id().equals(mensajeId))
        .findFirst()
        .orElseThrow()
        .textoLegible()
        .orElse(null);
  }

  private LoteIngesta procesar(String nombreDelChat, List<MensajeCrudo> crudos) {
    LoteIngesta nuevo =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/b.zip", T);
    lotes.guardar(nuevo);
    ExtractorFalso extractor =
        ExtractorFalso.porTexto(texto -> prenda("Camiseta estilo Prada", 50000));
    return casoCon(new FuenteFija(nombreDelChat, crudos), extractor).ejecutar(nuevo.id());
  }

  /**
   * Meraki, 9 de octubre de 2026: primero MerakiMen.zip y después Meraki.zip. La polo Prada viene
   * en los dos; en el general se descarta sin mirar fotos —aquí con otra foto—, porque su lugar es
   * el chat de caballero.
   */
  @Test
  void conElChatDeCaballeroPrimeroElGeneralDescartaLoQueEseYaTrajo() {
    conLosChatsDeVerdad();

    LoteIngesta deCaballero = procesar(CHAT_DE_CABALLERO, poloPrada("IMG-MEN-0159.jpg", T));
    LoteIngesta general =
        procesar(CHAT_GENERAL, poloPrada("00000200-PHOTO.jpg", T.plusSeconds(3 * 3600)));

    assertTrue(deCaballero.esChatDeCaballero());
    assertFalse(general.esChatDeCaballero());
    assertEquals(1, deCaballero.resumen().orElseThrow().borradoresNuevos());
    assertEquals(0, general.resumen().orElseThrow().borradoresNuevos());
    assertEquals(1, general.resumen().orElseThrow().descartes());
    assertEquals(
        1,
        borradores.porId.values().stream()
            .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
            .count());
  }

  /**
   * Al revés: si el general se procesó primero, cuando llega el de caballero el borrador del
   * general se rechaza con el motivo escrito, y queda el de caballero.
   */
  @Test
  void conElGeneralPrimeroSuBorradorSeRechazaCuandoLlegaElDeCaballero() {
    conLosChatsDeVerdad();

    procesar(CHAT_GENERAL, poloPrada("00000200-PHOTO.jpg", T.plusSeconds(3 * 3600)));
    procesar(CHAT_DE_CABALLERO, poloPrada("IMG-MEN-0159.jpg", T));

    List<BorradorProducto> todos = List.copyOf(borradores.porId.values());
    assertEquals(2, todos.size());
    assertEquals(EstadoBorrador.RECHAZADO, todos.get(0).estado());
    assertTrue(todos.get(0).motivoRechazo().orElseThrow().contains("chat de caballero"));
    assertEquals(EstadoBorrador.EN_REVISION, todos.get(1).estado());
  }

  /** Un proveedor de un solo chat no cambia: sin chat de caballero no hay nada que descartar. */
  @Test
  void sinChatDeCaballeroNadaSeDescartaPorEsto() {
    conLosChatsDeVerdad();

    LoteIngesta uno =
        procesar(CHAT_GENERAL, poloPrada("00000200-PHOTO.jpg", T.plusSeconds(3 * 3600)));

    assertEquals(1, uno.resumen().orElseThrow().borradoresNuevos());
  }

  /** El lote del chat de caballero de un zip le pide a la fuente ese chat, y queda marcado. */
  @Test
  void elLoteDeUnChatDelZipLeeSoloEseChat() {
    lote.leerSoloElChat(ChatDelZip.CABALLERO);
    FuenteFija fuente = new FuenteFija("MerakiMen", poloPrada("IMG-MEN-0159.jpg", T));

    LoteIngesta resultado =
        casoCon(fuente, ExtractorFalso.porTexto(texto -> prenda("Camiseta estilo Prada", 50000)))
            .ejecutar(lote.id());

    assertEquals(ChatDelZip.CABALLERO, fuente.ultimoChat);
    assertTrue(resultado.esChatDeCaballero());
    assertEquals(1, resultado.resumen().orElseThrow().borradoresNuevos());
  }
}
