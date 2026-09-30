package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.AlmacenDeImagenesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.ProcesadorNulo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioAtributosFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioCategoriasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioInventarioEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioMarcasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.application.proveedores.AprobarBorradorComando.FotoAprobada;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.OrigenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AprobarBorradorTest {

  private static final Instant FECHA_DEL_MENSAJE = Instant.parse("2026-09-28T15:15:00Z");
  private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final RepositorioBorradoresEnMemoria borradores = new RepositorioBorradoresEnMemoria();
  private final RepositorioProductosEnMemoria productos = new RepositorioProductosEnMemoria();
  private final RepositorioAtributosFijo atributos = new RepositorioAtributosFijo();
  private final RepositorioInventarioEnMemoria inventario = new RepositorioInventarioEnMemoria();
  private final AlmacenEnMemoria almacenPrivado = new AlmacenEnMemoria();
  private final AlmacenDeImagenesEnMemoria almacenPublico = new AlmacenDeImagenesEnMemoria();

  private Proveedor proveedor;
  private BorradorProducto borrador;
  private MensajeProveedor foto1;
  private MensajeProveedor foto2;

  @BeforeEach
  void unBorradorConDosFotos() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", AHORA);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("p"),
            FECHA_DEL_MENSAJE,
            "Bolso 💰 53.000");
    foto1 = foto(lote, "f1", "proveedores/x/2026/09/f1.jpg", "foto-negra");
    foto2 = foto(lote, "f2", "proveedores/x/2026/09/f2.jpg", "foto-vino");
    mensajes.guardarTodos(List.of(principal, foto1, foto2));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(foto1);
    publicacion.anexar(foto2);
    publicaciones.guardarTodas(List.of(publicacion));
    borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Bolso de dama mediano",
                LineaCatalogo.BOLSOS,
                TipoProductoProveedor.BOLSO,
                Dinero.deCop(53000),
                Tallas.desconocida(),
                2,
                List.of(),
                "importado",
                List.of("2 compartimientos internos", "incluye llavero"),
                new BigDecimal("0.9"),
                null),
            "{}",
            Dinero.deCop(53000),
            Dinero.deCop(71600),
            HuellaProveedor.calcular(proveedor.id(), "Bolso de dama mediano", Dinero.deCop(53000)),
            null,
            Set.of(),
            AHORA);
    borradores.guardar(borrador);
  }

  private MensajeProveedor foto(LoteIngesta lote, String id, String referencia, String contenido) {
    almacenPrivado.guardar(referencia, "image/jpeg", contenido.getBytes(StandardCharsets.UTF_8));
    return MensajeProveedor.imagen(
        proveedor.id(),
        lote.id(),
        new IdExternoDeMensaje(id),
        FECHA_DEL_MENSAJE.plusSeconds(10),
        null,
        referencia);
  }

  private AprobarBorrador caso() {
    AgregarVariante agregarVariante =
        new AgregarVariante(productos, atributos, inventario, new RelojFalso(AHORA), false);
    return new AprobarBorrador(
        borradores,
        publicaciones,
        mensajes,
        proveedores,
        productos,
        new RepositorioMarcasFijo(),
        new RepositorioCategoriasFijo(),
        atributos,
        agregarVariante,
        almacenPrivado,
        almacenPublico,
        new ProcesadorNulo());
  }

  private AprobarBorradorComando comando(List<FotoAprobada> fotos) {
    return new AprobarBorradorComando(
        borrador.id(),
        "Bolso de dama mediano",
        null,
        ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
        ApoyoDeCatalogoParaIngesta.MARCA.id(),
        71600,
        null,
        1,
        "Bolso de dama mediano",
        "Medium ladies bag",
        fotos);
  }

  /**
   * El tercer criterio de aceptación: un producto visible con sus fotos y sus variantes de color.
   */
  @Test
  void aprobarCreaUnProductoPublicadoDisponibleConSusVariantesYSusFotos() {
    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000"),
                        new FotoAprobada(foto2.id(), "Vino", null))));

    assertEquals(EstadoProducto.PUBLICADO, producto.estado());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    assertEquals(OrigenProducto.PROVEEDOR, producto.origen());
    assertEquals(Optional.of(proveedor.id()), producto.proveedorId());
    assertEquals(
        Optional.of(FECHA_DEL_MENSAJE),
        producto.vistoPorUltimaVez(),
        "visto en la fecha del mensaje");
    assertEquals(Optional.of(Dinero.deCop(53000)), producto.precioProveedor());
    assertEquals(
        "2 compartimientos internos\nincluye llavero\nMaterial: importado", producto.descripcion());

    List<Variante> variantes = producto.variantes();
    assertEquals(2, variantes.size());
    assertEquals(Dinero.deCop(71600), variantes.get(0).precio());
    assertEquals("Negro", variantes.get(0).atributos().get(0).valor());
    assertEquals("#000000", variantes.get(0).atributos().get(0).colorHex());
    assertEquals("Vino", variantes.get(1).atributos().get(0).valor());
    assertTrue(variantes.get(0).sku().valor().startsWith("PRV-"));
    assertEquals(
        1, inventario.buscarPorVarianteId(variantes.get(0).id()).orElseThrow().saldoTotal());

    ImagenProducto principal = producto.imagenPrincipal().orElseThrow();
    assertEquals(TipoImagen.PRINCIPAL, principal.tipo());
    assertEquals(
        Optional.of(variantes.get(0).id()),
        principal.varianteId(),
        "la foto negra cuelga de la negra");
    assertEquals("Bolso de dama mediano", principal.altEs());
    assertEquals(1, producto.galeria().size());
    assertEquals(Optional.of(variantes.get(1).id()), producto.galeria().get(0).varianteId());
    assertTrue(
        principal
            .url()
            .startsWith("https://publico.local/productos/" + producto.id() + "/principal-"));
    assertEquals(2, almacenPublico.objetos.size(), "las dos fotos se copiaron al bucket público");

    assertEquals(
        EstadoBorrador.APROBADO, borradores.buscarPorId(borrador.id()).orElseThrow().estado());
    assertEquals(Optional.of(producto.id()), borrador.productoId());
  }

  @Test
  void sinTonosNaceUnaSolaVarianteSinColorYConLaTallaDelBorrador() {
    Producto producto =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    borrador.id(),
                    null,
                    "A mano",
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    70000,
                    Tallas.lista(List.of("M", "L")),
                    3,
                    "Bolso",
                    "Bag",
                    List.of(new FotoAprobada(foto1.id(), null, null))));

    assertEquals(2, producto.variantes().size(), "una por talla");
    assertEquals("M", producto.variantes().get(0).atributos().get(0).valor());
    assertEquals("A mano", producto.descripcion());
    assertEquals("Bolso de dama mediano", producto.nombre(), "el título del borrador");
    assertEquals(
        3,
        inventario
            .buscarPorVarianteId(producto.variantes().get(1).id())
            .orElseThrow()
            .saldoTotal());
    assertTrue(producto.imagenPrincipal().orElseThrow().varianteId().isEmpty());
  }

  @Test
  void sinFotosNoSeAprueba() {
    assertThrows(BorradorSinFotosException.class, () -> caso().ejecutar(comando(List.of())));
    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());
    assertTrue(productos.porId.isEmpty());
  }

  @Test
  void unaFotoQueNoEsDelBorradorSeRechaza() {
    assertThrows(
        FotoNoEsDelBorradorException.class,
        () ->
            caso().ejecutar(comando(List.of(new FotoAprobada(UUID.randomUUID(), "Negro", null)))));
  }

  @Test
  void laCategoriaTieneQueSerHoja() {
    AprobarBorradorComando enRama =
        new AprobarBorradorComando(
            borrador.id(),
            "Bolso",
            null,
            ApoyoDeCatalogoParaIngesta.BOLSOS_DAMA.id(),
            ApoyoDeCatalogoParaIngesta.MARCA.id(),
            70000,
            null,
            1,
            "Bolso",
            "Bag",
            List.of(new FotoAprobada(foto1.id(), null, null)));

    assertThrows(CategoriaNoEsHojaException.class, () -> caso().ejecutar(enRama));
  }

  @Test
  void sinAtributoDeColorEnElCatalogoNoSePuedenArmarLosTonos() {
    atributos.atributos.remove(ApoyoDeCatalogoParaIngesta.COLOR);

    assertThrows(
        AtributoDeCatalogoNoDefinidoException.class,
        () -> caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), "Negro", null)))));
  }

  @Test
  void unBorradorQueYaNoEstaEnRevisionNoSeAprueba() {
    borrador.rechazar("No nos interesa.");
    borradores.actualizar(borrador);

    assertThrows(
        BorradorNoEditableException.class,
        () -> caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), null, null)))));
  }

  @Test
  void dosProductosConElMismoTituloNoChocanEnElSlug() {
    caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), null, null))));
    BorradorProducto otro =
        BorradorProducto.nuevo(
            borrador.publicacionId(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Bolso de dama mediano",
                null,
                null,
                Dinero.deCop(60000),
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ONE,
                null),
            "{}",
            Dinero.deCop(60000),
            Dinero.deCop(81000),
            HuellaProveedor.calcular(proveedor.id(), "Bolso de dama mediano", Dinero.deCop(60000)),
            null,
            Set.of(),
            AHORA);
    borradores.guardar(otro);

    Producto segundo =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    otro.id(),
                    null,
                    null,
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    81000,
                    null,
                    1,
                    "Bolso",
                    "Bag",
                    List.of(new FotoAprobada(foto2.id(), null, null))));

    assertEquals("bolso-de-dama-mediano-2", segundo.slug().valor());
  }
}
