package co.tecnosport.api.application.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.difusion.EstadoPublicacion;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DifundirProductoTest {

  private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");
  private static final String VISTA_PREVIA = "https://storage.googleapis.com/b/principal.jpg";

  private final RepositorioPublicacionesFalso publicaciones = new RepositorioPublicacionesFalso();
  private final PublicadorFalso publicador = new PublicadorFalso();
  private final AjustadorFalso ajustador = new AjustadorFalso();

  @Test
  void publicaEnLaRedPedidaYDejaLaConstanciaEnPublicada() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    List<PublicacionEnRed> resultado =
        caso.ejecutar(
            new DifundirProductoComando(producto.id(), List.of(RedSocial.INSTAGRAM), null));

    assertEquals(1, resultado.size());
    assertEquals(EstadoPublicacion.PUBLICADA, resultado.get(0).estado());
    assertEquals("18196134166390376", resultado.get(0).idPublicacionExterna().orElseThrow());
    assertEquals(List.of(VISTA_PREVIA), publicador.ultimasUrls);
  }

  /**
   * La que importa de todo el caso de uso: que Instagram rechace no puede borrar el post que
   * Facebook ya publicó. Nada de lo que hagamos aquí lo despublica.
   */
  @Test
  void siUnaRedFallaLaOtraSePublicaIgual() {
    Producto producto = publicado();
    publicador.fallaEn = RedSocial.INSTAGRAM;
    DifundirProducto caso = casoDeUso(producto);

    List<PublicacionEnRed> resultado =
        caso.ejecutar(
            new DifundirProductoComando(
                producto.id(), List.of(RedSocial.FACEBOOK, RedSocial.INSTAGRAM), null));

    assertEquals(2, resultado.size());
    assertEquals(EstadoPublicacion.PUBLICADA, resultado.get(0).estado());
    assertEquals(EstadoPublicacion.FALLIDA, resultado.get(1).estado());
    assertEquals(
        "La imagen no se pudo descargar.", resultado.get(1).detalleDelFallo().orElseThrow());
  }

  /**
   * Se guarda antes de llamar a la red, no después: una caída en mitad del viaje tiene que dejar
   * rastro de que se intentó. Si solo se guardara el resultado, un post ya publicado podría no
   * existir en la base y nadie sabría que salió.
   */
  @Test
  void dejaLaConstanciaEnPendienteAntesDeLlamarALaRed() {
    Producto producto = publicado();
    publicador.alPublicar =
        () ->
            assertEquals(
                EstadoPublicacion.PENDIENTE,
                publicaciones.guardadas.get(0).estado(),
                "la fila tiene que existir y estar PENDIENTE antes de que la red conteste");
    DifundirProducto caso = casoDeUso(producto);

    caso.ejecutar(new DifundirProductoComando(producto.id(), List.of(RedSocial.FACEBOOK), null));

    assertEquals(EstadoPublicacion.PUBLICADA, publicaciones.guardadas.get(0).estado());
  }

  @Test
  void elPieEscritoAManoSeUsaTalCual() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    caso.ejecutar(
        new DifundirProductoComando(
            producto.id(), List.of(RedSocial.FACEBOOK), "Lo escribí yo y así se queda."));

    assertEquals("Lo escribí yo y así se queda.", publicador.ultimoPie);
  }

  @Test
  void sinPieEscritoSeArmaElPropuesto() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    caso.ejecutar(new DifundirProductoComando(producto.id(), List.of(RedSocial.FACEBOOK), "   "));

    assertTrue(publicador.ultimoPie.startsWith("JBL Grip - $299.900"), publicador.ultimoPie);
  }

  @Test
  void elDobleClicSeRechazaEnVezDeDuplicarElPost() {
    Producto producto = publicado();
    publicaciones.hayReciente = true;
    DifundirProducto caso = casoDeUso(producto);

    DifusionRepetidaException error =
        assertThrows(
            DifusionRepetidaException.class,
            () ->
                caso.ejecutar(
                    new DifundirProductoComando(
                        producto.id(), List.of(RedSocial.INSTAGRAM), null)));

    assertTrue(error.getMessage().contains("se acaba de mandar"), error.getMessage());
    assertEquals(0, publicador.veces);
  }

  @Test
  void pedirDosVecesLaMismaRedPublicaUnaSolaVez() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    List<PublicacionEnRed> resultado =
        caso.ejecutar(
            new DifundirProductoComando(
                producto.id(), List.of(RedSocial.FACEBOOK, RedSocial.FACEBOOK), null));

    assertEquals(1, resultado.size());
    assertEquals(1, publicador.veces);
  }

  @Test
  void unBorradorNoSeAnunciaPorqueSuFichaResponde404() {
    Producto borrador = ApoyoDeDifusion.jblGrip(EstadoProducto.BORRADOR, VISTA_PREVIA);
    DifundirProducto caso = casoDeUso(borrador);

    ProductoNoDifundibleException error =
        assertThrows(
            ProductoNoDifundibleException.class,
            () ->
                caso.ejecutar(
                    new DifundirProductoComando(borrador.id(), List.of(RedSocial.FACEBOOK), null)));

    assertTrue(error.getMessage().contains("404"), error.getMessage());
    assertEquals(0, publicador.veces);
  }

  @Test
  void sinImagenPrincipalNoHayNadaQuePublicar() {
    Producto sinImagen = ApoyoDeDifusion.jblGrip(EstadoProducto.PUBLICADO, null, false);
    DifundirProducto caso = casoDeUso(sinImagen);

    ProductoNoDifundibleException error =
        assertThrows(
            ProductoNoDifundibleException.class,
            () ->
                caso.ejecutar(
                    new DifundirProductoComando(
                        sinImagen.id(), List.of(RedSocial.FACEBOOK), null)));

    assertTrue(error.getMessage().contains("no tiene imagen principal"), error.getMessage());
  }

  /** La imagen del sitio es AVIF y Meta no lo entiende: sin la vista previa JPEG no hay envío. */
  @Test
  void sinVistaPreviaEnJpegTampoco() {
    Producto sinPrevia = ApoyoDeDifusion.jblGrip(EstadoProducto.PUBLICADO, null);
    DifundirProducto caso = casoDeUso(sinPrevia);

    ProductoNoDifundibleException error =
        assertThrows(
            ProductoNoDifundibleException.class,
            () ->
                caso.ejecutar(
                    new DifundirProductoComando(
                        sinPrevia.id(), List.of(RedSocial.FACEBOOK), null)));

    assertTrue(error.getMessage().contains("AVIF"), error.getMessage());
  }

  /**
   * <b>El defecto que tenía la difusión rota para medio catálogo.</b> Un producto aprobado desde un
   * borrador de proveedor publica la foto tal como llegó -- JPEG -- y nunca genera vista previa.
   * Aquí se exigía la vista previa y punto, así que esos productos se rechazaban por no tener una
   * conversión a JPEG de algo que ya era JPEG: ni se podían difundir ni se podía siquiera proponer
   * el pie, y el panel solo decía que revisaras que el producto estuviera publicado y tuviera
   * imagen principal, que era falso en las dos mitades.
   */
  @Test
  void unaPrincipalQueYaEsJpegNoNecesitaVistaPrevia() {
    Producto deProveedor = ApoyoDeDifusion.jblGripConPrincipalJpeg();
    DifundirProducto caso = casoDeUso(deProveedor);

    caso.ejecutar(new DifundirProductoComando(deProveedor.id(), List.of(RedSocial.FACEBOOK), null));

    assertEquals(List.of(ApoyoDeDifusion.PRINCIPAL_JPEG), publicador.ultimasUrls);
  }

  /** Y proponer el pie pasa por la misma guarda, que es donde el panel se atascaba. */
  @Test
  void elPieSePuedeProponerParaUnProductoDeProveedor() {
    Producto deProveedor = ApoyoDeDifusion.jblGripConPrincipalJpeg();

    assertTrue(
        casoDeUso(deProveedor)
            .proponerPie(deProveedor.id(), RedSocial.INSTAGRAM)
            .startsWith("JBL Grip"));
  }

  /** El post es el carrusel de la ficha: la principal primero y detrás la galería, en su orden. */
  @Test
  void seMandaLaGaleriaEnteraConLaPrincipalDelante() {
    Producto conGaleria =
        ApoyoDeDifusion.jblGripConGaleria(
            List.of(
                ApoyoDeDifusion.deGaleria(0, "https://b/uno.jpg", 1000, 1000),
                ApoyoDeDifusion.deGaleria(1, "https://b/dos.jpg", 1000, 1000)));
    DifundirProducto caso = casoDeUso(conGaleria);

    caso.ejecutar(new DifundirProductoComando(conGaleria.id(), List.of(RedSocial.FACEBOOK), null));

    assertEquals(
        List.of(ApoyoDeDifusion.PRINCIPAL_JPEG, "https://b/uno.jpg", "https://b/dos.jpg"),
        publicador.ultimasUrls);
  }

  /**
   * Una foto de galería que Meta no sabe leer se cae del carrusel y ya; con la principal sería otra
   * cosa, porque esa encabeza el post. Una foto menos no es motivo para no publicar.
   */
  @Test
  void unaFotoDeGaleriaIlegibleSeQuedaFueraSinTumbarElPost() {
    Producto conGaleria =
        ApoyoDeDifusion.jblGripConGaleria(
            List.of(
                ApoyoDeDifusion.deGaleria(0, "https://b/uno.avif", 1000, 1000),
                ApoyoDeDifusion.deGaleria(1, "https://b/dos.jpg", 1000, 1000)));
    DifundirProducto caso = casoDeUso(conGaleria);

    caso.ejecutar(new DifundirProductoComando(conGaleria.id(), List.of(RedSocial.FACEBOOK), null));

    assertEquals(
        List.of(ApoyoDeDifusion.PRINCIPAL_JPEG, "https://b/dos.jpg"), publicador.ultimasUrls);
  }

  /**
   * Y la constancia guarda <b>lo que la red admitió</b>, no lo que se le ofreció. Si guardara lo
   * ofrecido, la fila diría que salió una foto que Instagram nunca publicó, y esa fila es justo lo
   * que alguien mira para saber qué vio la gente.
   */
  @Test
  void laConstanciaGuardaSoloLasFotosQueLaRedAdmitio() {
    Producto conGaleria =
        ApoyoDeDifusion.jblGripConGaleria(
            List.of(ApoyoDeDifusion.deGaleria(0, "https://b/uno.jpg", 1000, 1000)));
    publicador.soloAdmiteLaPrimera = true;
    DifundirProducto caso = casoDeUso(conGaleria);

    List<PublicacionEnRed> resultado =
        caso.ejecutar(
            new DifundirProductoComando(conGaleria.id(), List.of(RedSocial.INSTAGRAM), null));

    assertEquals(List.of(ApoyoDeDifusion.PRINCIPAL_JPEG), resultado.get(0).urlsImagen());
  }

  /**
   * Cuando la red pide una proporción, las fotos se encajan <b>antes</b> de preguntarle cuáles
   * admite. En ese orden, y no al revés: encajando después, el filtro ya habría descartado justo
   * las que el encaje iba a salvar, y el carrusel saldría corto sin que nada lo dijera.
   */
  @Test
  void lasFotosSeEncajanAntesDePreguntarCualesAdmiteLaRed() {
    Producto conGaleria =
        ApoyoDeDifusion.jblGripConGaleria(
            List.of(ApoyoDeDifusion.deGaleria(0, "https://b/alta.jpg", 600, 1200)));
    publicador.proporcionPedida = OptionalDouble.of(0.8);
    DifundirProducto caso = casoDeUso(conGaleria);

    caso.ejecutar(new DifundirProductoComando(conGaleria.id(), List.of(RedSocial.INSTAGRAM), null));

    assertEquals(List.of(0.8, 0.8), ajustador.proporcionesPedidas, "las dos, principal incluida");
    assertTrue(
        publicador.ultimasUrls.stream().allMatch(url -> url.endsWith("?encajada")),
        publicador.ultimasUrls.toString());
  }

  /** Sin proporción pedida —Facebook— no se encaja nada: serían objetos en el bucket para nada. */
  @Test
  void sinProporcionPedidaNoSeEncajaNinguna() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    caso.ejecutar(new DifundirProductoComando(producto.id(), List.of(RedSocial.FACEBOOK), null));

    assertTrue(ajustador.proporcionesPedidas.isEmpty());
  }

  /**
   * Y la que no se pudo encajar sigue adelante tal como está: será la red quien diga si cabe.
   * Dejarla fuera aquí convertiría un fallo al leer el bucket en una foto menos sin que nadie se
   * entere, y eso pasa a diario con el catálogo sembrado de picsum.photos.
   */
  @Test
  void laQueNoSePudoEncajarSigueComoEstaba() {
    Producto producto = publicado();
    publicador.proporcionPedida = OptionalDouble.of(0.8);
    ajustador.noPuede = true;
    DifundirProducto caso = casoDeUso(producto);

    caso.ejecutar(new DifundirProductoComando(producto.id(), List.of(RedSocial.INSTAGRAM), null));

    assertEquals(List.of(VISTA_PREVIA), publicador.ultimasUrls);
  }

  /** Si la red no admite ni una, no se escribe una fila que diga que se publicó algo. */
  @Test
  void siLaRedNoAdmiteNingunaFotoNoSePublicaNiSeGuarda() {
    Producto producto = publicado();
    publicador.noAdmiteNinguna = true;
    DifundirProducto caso = casoDeUso(producto);

    ProductoNoDifundibleException error =
        assertThrows(
            ProductoNoDifundibleException.class,
            () ->
                caso.ejecutar(
                    new DifundirProductoComando(
                        producto.id(), List.of(RedSocial.INSTAGRAM), null)));

    assertTrue(error.getMessage().contains("INSTAGRAM"), error.getMessage());
    assertEquals(0, publicador.veces);
    assertTrue(publicaciones.guardadas.isEmpty());
  }

  @Test
  void unProductoQueNoExisteNoSeDifunde() {
    DifundirProducto caso = casoDeUso(null);

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            caso.ejecutar(
                new DifundirProductoComando(UUID.randomUUID(), List.of(RedSocial.FACEBOOK), null)));
  }

  @Test
  void hayQueDecirEnQueRedSeDifunde() {
    Producto producto = publicado();
    DifundirProducto caso = casoDeUso(producto);

    assertThrows(
        IllegalArgumentException.class,
        () -> caso.ejecutar(new DifundirProductoComando(producto.id(), List.of(), null)));
  }

  // --- armado ---

  private static Producto publicado() {
    return ApoyoDeDifusion.jblGrip(EstadoProducto.PUBLICADO, VISTA_PREVIA);
  }

  private DifundirProducto casoDeUso(Producto producto) {
    return new DifundirProducto(
        new ApoyoDeDifusion.RepositorioProductosFalso(producto),
        publicaciones,
        publicador,
        ajustador,
        new ArmadorDePieDeFoto("https://www.tecnosport.co", List.of()),
        // La transacción propia, en una prueba, es simplemente ejecutar: lo que se comprueba aquí
        // es el orden de las escrituras, no que Postgres confirme.
        new EnTransaccionPropia() {
          @Override
          public <T> T ejecutar(java.util.function.Supplier<T> trabajo) {
            return trabajo.get();
          }
        },
        (Reloj) () -> AHORA);
  }

  /** Doble escrito a mano: unas líneas hacen el trabajo y no hace falta Mockito. */
  private static final class PublicadorFalso implements PublicadorEnRedSocial {
    private RedSocial fallaEn;
    private Runnable alPublicar = () -> {};
    private String ultimoPie;
    private List<String> ultimasUrls = List.of();
    private int veces;
    private boolean soloAdmiteLaPrimera;
    private boolean noAdmiteNinguna;
    private OptionalDouble proporcionPedida = OptionalDouble.empty();

    @Override
    public OptionalDouble proporcionDelCarrusel(RedSocial red, List<ImagenAPublicar> imagenes) {
      return proporcionPedida;
    }

    @Override
    public List<ImagenAPublicar> admitidasPor(RedSocial red, List<ImagenAPublicar> imagenes) {
      if (noAdmiteNinguna) {
        return List.of();
      }
      return soloAdmiteLaPrimera ? List.of(imagenes.get(0)) : List.copyOf(imagenes);
    }

    @Override
    public ResultadoPublicacion publicar(
        RedSocial red, List<ImagenAPublicar> imagenes, String pieDeFoto) {
      veces++;
      ultimoPie = pieDeFoto;
      ultimasUrls = imagenes.stream().map(ImagenAPublicar::url).toList();
      alPublicar.run();
      return red == fallaEn
          ? ResultadoPublicacion.fallida("La imagen no se pudo descargar.")
          : ResultadoPublicacion.publicada("18196134166390376");
    }
  }

  /**
   * Encaja marcando la URL, que es lo que la prueba necesita ver. El ajuste de verdad —abrir los
   * bytes y redibujarlos— lo cubre {@code AjustadorDeImagenesJava2DTest} contra imágenes reales.
   */
  private static final class AjustadorFalso implements AjustadorDeImagenes {
    private boolean noPuede;
    private final List<Double> proporcionesPedidas = new ArrayList<>();

    @Override
    public Optional<ImagenAPublicar> ajustarA(
        UUID productoId, ImagenAPublicar imagen, double proporcionObjetivo) {
      proporcionesPedidas.add(proporcionObjetivo);
      if (noPuede) {
        return Optional.empty();
      }
      int alto = 1000;
      int ancho = (int) Math.round(alto * proporcionObjetivo);
      return Optional.of(new ImagenAPublicar(imagen.url() + "?encajada", ancho, alto));
    }
  }

  private static final class RepositorioPublicacionesFalso implements RepositorioPublicaciones {
    private final List<PublicacionEnRed> guardadas = new ArrayList<>();
    private boolean hayReciente;

    @Override
    public void guardar(PublicacionEnRed publicacion) {
      if (!guardadas.contains(publicacion)) {
        guardadas.add(publicacion);
      }
    }

    @Override
    public java.util.Optional<PublicacionEnRed> ultimaDe(UUID productoId, RedSocial red) {
      return guardadas.stream().filter(p -> p.red() == red).reduce((a, b) -> b);
    }

    @Override
    public List<PublicacionEnRed> historialDe(UUID productoId) {
      return List.copyOf(guardadas);
    }

    @Override
    public boolean hayUnaReciente(UUID productoId, RedSocial red, Instant desde) {
      return hayReciente;
    }
  }
}
