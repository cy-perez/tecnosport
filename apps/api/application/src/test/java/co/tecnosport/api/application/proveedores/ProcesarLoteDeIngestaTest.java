package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.CalculadorDePHashPorContenido;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
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
        new RegistrarMensajesDeProveedor(proveedores, lotes, mensajes, almacen);
    ArmarPublicaciones armar =
        new ArmarPublicaciones(
            lotes, mensajes, publicaciones, new AgrupadorDePublicaciones(Duration.ofMinutes(15)));
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
              List.of(),
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
    assertEquals(Optional.of(Dinero.deCop(71600)), porPublicacion.get(0).precioVentaSugerido());
    assertEquals(
        Optional.of(Dinero.deCop(78000)),
        porPublicacion.get(5).precioVentaSugerido(),
        "ropa al 1,30");
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
   * en los dos. Salen tres borradores de dos publicaciones —el jean una sola vez—, todos con las
   * fotos compartidas y sin huella visual.
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
                        prenda("Chaleco Denim", 99000), prenda("Jean wide Leg Licrado", 124000))
                    : List.of(
                        prenda("Blusa Rib larga", 28000), prenda("Jean wide Leg Licrado", 124000)));

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
        List.of(),
        new BigDecimal("0.9"),
        null);
  }
}
