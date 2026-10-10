package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.AlmacenDeImagenesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.CalculadorDePHashPorContenido;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.ProcesadorNulo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioAtributosFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioBorradoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioCategoriasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioInventarioEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioMarcasFijo;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
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
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.FotosDelProducto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TallasPorTono;
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
                "2 compartimientos internos. incluye llavero.",
                null,
                false,
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
        new RepositorioProductosDeProveedorEnMemoria(productos),
        new RepositorioMarcasFijo(),
        new RepositorioCategoriasFijo(),
        atributos,
        agregarVariante,
        almacenPrivado,
        almacenPublico,
        new ProcesadorNulo(),
        new CalculadorDePHashPorContenido(),
        new RelojFalso(AHORA));
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
    // Visto al aprobar y no en la fecha del mensaje: dos días después, y con la ventana de tres
    // el job lo habría ocultado en su primera vuelta.
    assertEquals(Optional.of(AHORA), producto.vistoPorUltimaVez(), "visto al aprobar");
    assertEquals(Optional.of(Dinero.deCop(53000)), producto.precioProveedor());
    assertEquals(
        "2 compartimientos internos. incluye llavero.",
        producto.descripcion(),
        "la del borrador, cuando la aprobación no trae otra");

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
    // La principal cuelga de la variante de su tono —la negra— desde el 7 de octubre de 2026.
    // Antes se le forzaba el nulo por el índice único, y con eso se descartaba el color que quien
    // revisa le había marcado a esa foto; `V87` ensanchó el índice y ya no hace falta.
    assertEquals(
        Optional.of(variantes.get(0).id()), principal.varianteId(), "la principal es la negra");
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

  /**
   * La talla única es una talla: la variante la lleva como «Única» y el producto guarda hasta qué
   * talla sirve, que es lo que la tarjeta y la ficha dicen al lado.
   */
  @Test
  void laTallaUnicaQuedaEnLaVarianteYElSirveHastaEnElProducto() {
    Producto producto =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    borrador.id(),
                    null,
                    "Bodi con herraje.",
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    70000,
                    Tallas.unica("L"),
                    1,
                    "Bodi",
                    "Bodysuit",
                    List.of(new FotoAprobada(foto1.id(), null, null))));

    assertEquals(1, producto.variantes().size());
    assertEquals("Única", producto.variantes().get(0).atributos().get(0).valor());
    assertEquals(Optional.of("L"), producto.tallaSirveHasta());
  }

  @Test
  void unaTallaUnicaSinSirveHastaNoLoInventa() {
    Producto producto =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    borrador.id(),
                    null,
                    "Bodi con herraje.",
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    70000,
                    Tallas.unica(null),
                    1,
                    "Bodi",
                    "Bodysuit",
                    List.of(new FotoAprobada(foto1.id(), null, null))));

    assertEquals("Única", producto.variantes().get(0).atributos().get(0).valor());
    assertEquals(Optional.empty(), producto.tallaSirveHasta());
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

  /**
   * El anuncio repetido: el primer borrador ya se aprobó y el segundo tiene la misma huella. Sin
   * esto, el índice único respondía con un 500 al aprobar el segundo.
   */
  @Test
  void elMismoAnuncioYaAprobadoNoSeApruebaDosVeces() {
    caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), null, null))));
    BorradorProducto repetido =
        BorradorProducto.nuevo(
            borrador.publicacionId(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Bolso de dama mediano",
                LineaCatalogo.BOLSOS,
                TipoProductoProveedor.BOLSO,
                Dinero.deCop(53000),
                Tallas.desconocida(),
                0,
                List.of(),
                null,
                null,
                null,
                false,
                new BigDecimal("0.9"),
                null),
            "{}",
            Dinero.deCop(53000),
            Dinero.deCop(71600),
            borrador.huella().orElseThrow(),
            null,
            Set.of(),
            AHORA);
    borradores.guardar(repetido);
    AprobarBorradorComando segundo =
        new AprobarBorradorComando(
            repetido.id(),
            null,
            "Bolso de dama mediano.",
            ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
            ApoyoDeCatalogoParaIngesta.MARCA.id(),
            71600,
            null,
            1,
            "alt",
            "alt",
            List.of(new FotoAprobada(foto2.id(), null, null)));

    assertThrows(ProductoDeProveedorYaExisteException.class, () -> caso().ejecutar(segundo));
    assertEquals(1, productos.porId.size(), "no se creó un duplicado");
    assertEquals(EstadoBorrador.EN_REVISION, repetido.estado());
  }

  /** Lo que ya subió al bucket público se borra si el resto falla: nada de huérfanos. */
  @Test
  void siFallaAMitadDeLasFotosBorraLasQueYaHabiaSubido() {
    MensajeProveedor ajena =
        MensajeProveedor.imagen(
            proveedor.id(),
            borrador.publicacionId(),
            new IdExternoDeMensaje("ajena"),
            FECHA_DEL_MENSAJE,
            null,
            "proveedores/x/2026/09/no-existe.jpg");

    assertThrows(
        FotoNoEsDelBorradorException.class,
        () ->
            caso()
                .ejecutar(
                    comando(
                        List.of(
                            new FotoAprobada(foto1.id(), null, null),
                            new FotoAprobada(ajena.id(), null, null)))));
    assertTrue(almacenPublico.objetos.isEmpty(), "sin fotos huérfanas en el bucket público");
  }

  /**
   * La principal conserva el tono que se le marcó, y <b>eso afirmaba lo contrario</b> hasta el 7 de
   * octubre de 2026: se le forzaba el nulo y el color se perdía.
   *
   * <p>Ahora ese color decide algo. Una principal que vale para todos los tonos encabeza la tarjeta
   * del catálogo y se queda fuera de la galería de la ficha —no retrata ninguna de las prendas que
   * se pueden elegir—; una con color, además abre la ficha.
   */
  @Test
  void laFotoPrincipalConservaElTonoQueSeLeMarco() {
    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000"),
                        new FotoAprobada(foto2.id(), "Vino", null))));

    assertEquals(
        Optional.of(producto.variantes().get(0).id()),
        producto.imagenPrincipal().orElseThrow().varianteId());
    assertTrue(producto.galeria().get(0).varianteId().isPresent(), "la galería sí lleva su tono");
  }

  /** Y sin tono marcado se queda sin variante: es la que vale para todos. */
  @Test
  void laFotoPrincipalSinTonoValeParaTodosLosTonos() {
    Producto producto = caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), null, null))));

    assertEquals(Optional.empty(), producto.imagenPrincipal().orElseThrow().varianteId());
  }

  /**
   * Dos fotos marcadas con el mismo color <b>y sin prenda</b> son <b>dos variantes</b>, y el valor
   * del atributo las numera para distinguirlas: es lo que el contrato hacía antes de tener el campo
   * {@code prenda}, y un cliente que no lo manda sigue obteniendo lo mismo.
   *
   * <p>Hasta el 6 de octubre de 2026 los tonos se agrupaban con {@code distinct()}, así que las dos
   * colapsaban en una variante con las dos fotos colgando: quien compra veía un solo círculo y no
   * podía elegir cuál de las dos prendas quería. Numerar es feo y es lo correcto -- el valor del
   * atributo es lo que el pedido congela y lo que lee quien empaca, y dos variantes con el mismo
   * texto se leen igual en el carrito, en el correo y en la guía.
   */
  @Test
  void dosFotosDelMismoTonoSonDosVariantesNumeradas() {
    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Azul oscuro", "#1B2A4A"),
                        new FotoAprobada(foto2.id(), "Azul oscuro", "#1B2A4A"))));

    assertEquals(2, producto.variantes().size());
    assertEquals("Azul oscuro 1", producto.variantes().get(0).atributos().get(0).valor());
    assertEquals("Azul oscuro 2", producto.variantes().get(1).atributos().get(0).valor());
    // Y cada foto cuelga de la suya: con el defecto, la de galería apuntaba a la única que
    // había y las dos variantes compartían foto.
    assertEquals(
        Optional.of(producto.variantes().get(1).id()), producto.galeria().get(0).varianteId());
  }

  /**
   * Una foto más en el borrador, subida desde el panel: el fixture trae dos de la publicación, y
   * las prendas de varias fotos piden más.
   */
  private UUID otraFoto(String nombre) {
    String referencia =
        "proveedores/" + proveedor.id() + "/borradores/" + borrador.id() + "/" + nombre + ".jpg";
    almacenPrivado.guardar(referencia, "image/jpeg", nombre.getBytes(StandardCharsets.UTF_8));
    FotoSubida subida = new FotoSubida(UUID.randomUUID(), referencia, AHORA);
    borrador.agregarFotoSubida(subida);
    borradores.actualizar(borrador);
    return subida.id();
  }

  private static List<String> coloresDe(Producto producto) {
    return producto.variantes().stream().map(v -> v.atributos().get(0).valor()).toList();
  }

  /**
   * Dos fotos de la misma prenda —dos ángulos del mismo bolso— son <b>una</b> variante con las dos
   * fotos, y su color no se numera: no hay otra prenda de la que distinguirlo. Del 6 al 7 de
   * octubre de 2026 salían «Rojo 1» y «Rojo 2», dos círculos para una sola prenda.
   */
  @Test
  void lasFotosDeUnaMismaPrendaSonUnaSolaVariante() {
    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Rojo", "#C0392B", 1),
                        new FotoAprobada(foto2.id(), "Rojo", "#C0392B", 1))));

    assertEquals(List.of("Rojo"), coloresDe(producto));
    UUID roja = producto.variantes().get(0).id();
    // La principal también: es una foto de esa prenda, y con color abre la ficha (ADR-0069).
    assertEquals(Optional.of(roja), producto.imagenPrincipal().orElseThrow().varianteId());
    assertEquals(Optional.of(roja), producto.galeria().get(0).varianteId());
  }

  /** Seis fotos, tres prendas de dos fotos cada una: tres variantes, cada una con sus dos fotos. */
  @Test
  void variasPrendasConVariasFotosCadaUna() {
    UUID foto3 = otraFoto("blanca-1");
    UUID foto4 = otraFoto("blanca-2");
    UUID foto5 = otraFoto("negra-1");
    UUID foto6 = otraFoto("negra-2");

    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Rojo", "#C0392B", 1),
                        new FotoAprobada(foto2.id(), "Rojo", "#C0392B", 1),
                        new FotoAprobada(foto3, "Blanco", "#FFFFFF", 2),
                        new FotoAprobada(foto4, "Blanco", "#FFFFFF", 2),
                        new FotoAprobada(foto5, "Negro", "#000000", 3),
                        new FotoAprobada(foto6, "Negro", "#000000", 3))));

    assertEquals(List.of("Rojo", "Blanco", "Negro"), coloresDe(producto));
    List<UUID> variantes = producto.variantes().stream().map(Variante::id).toList();
    assertEquals(
        Optional.of(variantes.get(0)), producto.imagenPrincipal().orElseThrow().varianteId());
    List<Optional<UUID>> deLaGaleria =
        producto.galeria().stream().map(ImagenProducto::varianteId).toList();
    assertEquals(
        List.of(
            Optional.of(variantes.get(0)),
            Optional.of(variantes.get(1)),
            Optional.of(variantes.get(1)),
            Optional.of(variantes.get(2)),
            Optional.of(variantes.get(2))),
        deLaGaleria);
  }

  /**
   * El caso que impide agrupar por color: un jean con cuatro diseños negros son cuatro prendas, y
   * se numeran. Con la prenda explícita el mismo color ya no decide nada solo.
   */
  @Test
  void prendasDistintasDelMismoColorSeNumeran() {
    UUID foto3 = otraFoto("negro-3");
    UUID foto4 = otraFoto("negro-4");

    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000", 1),
                        new FotoAprobada(foto2.id(), "Negro", "#000000", 2),
                        new FotoAprobada(foto3, "Negro", "#000000", 3),
                        new FotoAprobada(foto4, "Negro", "#000000", 4))));

    assertEquals(List.of("Negro 1", "Negro 2", "Negro 3", "Negro 4"), coloresDe(producto));
  }

  /**
   * Dos prendas negras de dos fotos cada una: el número distingue las prendas, no las fotos. Y el
   * orden de las prendas es el de su primera foto, no el número que traen.
   */
  @Test
  void elNumeroDistinguePrendasNoFotos() {
    UUID foto3 = otraFoto("negro-b");
    UUID foto4 = otraFoto("negro-a-2");

    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000", 7),
                        new FotoAprobada(foto2.id(), null, null, null),
                        new FotoAprobada(foto3, "Negro", "#000000", 2),
                        new FotoAprobada(foto4, "Negro", "#000000", 7))));

    assertEquals(List.of("Negro 1", "Negro 2"), coloresDe(producto));
    UUID primera = producto.variantes().get(0).id();
    assertEquals(Optional.of(primera), producto.imagenPrincipal().orElseThrow().varianteId());
    assertEquals(
        List.of(
            Optional.empty(), Optional.of(producto.variantes().get(1).id()), Optional.of(primera)),
        producto.galeria().stream().map(ImagenProducto::varianteId).toList(),
        "la foto sin prenda vale para todas");
  }

  /** Las prendas se combinan con las tallas igual que los tonos sueltos: prenda por talla. */
  @Test
  void cadaPrendaSeCombinaConCadaTalla() {
    Producto producto =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    borrador.id(),
                    null,
                    null,
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    70000,
                    Tallas.lista(List.of("S", "M")),
                    1,
                    "Bolso",
                    "Bag",
                    List.of(
                        new FotoAprobada(foto1.id(), "Rojo", "#C0392B", 1),
                        new FotoAprobada(foto2.id(), "Rojo", "#C0392B", 1))));

    assertEquals(2, producto.variantes().size(), "una prenda por dos tallas");
    assertEquals(List.of("Rojo", "Rojo"), coloresDe(producto));
  }

  /**
   * «Talla SM ML(negro) / Talla ML(cocoa)», Violeta, 10 de octubre de 2026: del cocoa no hay SM, y
   * no se crea esa variante aunque la blusa se apruebe en SM y ML. La paleta no tiene «cocoa»:
   * quien aprueba marca la foto «Café», y es el color que la lectura vio en esa foto el que la une
   * con sus tallas. Con el nombre solo, el café salía en SM.
   */
  @Test
  void unTonoSoloTieneLasTallasQueElMensajeLeDio() {
    borrador =
        BorradorProducto.nuevo(
            borrador.publicacionId(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Blusa licrada",
                LineaCatalogo.ROPA,
                TipoProductoProveedor.BLUSA,
                Dinero.deCop(38000),
                Tallas.lista(List.of("SM", "ML")),
                2,
                List.of("negro", "cocoa"),
                null,
                "Blusa licrada con herraje trasero.",
                null,
                false,
                new BigDecimal("0.9"),
                null,
                "VY2945",
                new TallasPorTono(
                    List.of(
                        new TallasPorTono.TallasDeUnTono("negro", List.of("SM", "ML")),
                        new TallasPorTono.TallasDeUnTono("cocoa", List.of("ML")))),
                List.of()),
            "{}",
            Dinero.deCop(38000),
            Dinero.deCop(58000),
            HuellaProveedor.deReferencia(proveedor.id(), "VY2945"),
            null,
            Set.of(),
            new FotosDelProducto(
                Set.of(), java.util.Map.of(foto1.id(), "negro", foto2.id(), "cocoa"), null),
            AHORA);
    borradores.guardar(borrador);

    Producto producto =
        caso()
            .ejecutar(
                new AprobarBorradorComando(
                    borrador.id(),
                    null,
                    null,
                    ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO.id(),
                    ApoyoDeCatalogoParaIngesta.MARCA.id(),
                    58000,
                    null,
                    1,
                    "Blusa",
                    "Top",
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000", 1),
                        new FotoAprobada(foto2.id(), "Café", "#6F4E37", 2))));

    List<String> combinaciones =
        producto.variantes().stream()
            .map(v -> v.atributos().get(0).valor() + "/" + v.atributos().get(1).valor())
            .toList();
    assertEquals(List.of("Negro/SM", "Negro/ML", "Café/ML"), combinaciones);
  }

  /**
   * Una foto sumada desde otra publicación se aprueba como las suyas: está en otro lote, y la
   * aprobación la encuentra por su id (10 de octubre de 2026).
   */
  @Test
  void unaFotoSumadaDeOtraPublicacionSeApruebaComoLasDemas() {
    LoteIngesta otroLote =
        LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/b.zip", AHORA);
    MensajeProveedor deOtroLote =
        foto(otroLote, "otra", "proveedores/x/2026/10/otra.jpg", "foto-otra");
    mensajes.guardarTodos(List.of(deOtroLote));
    borrador.agregarFotos(List.of(deOtroLote.id()), List.of(foto1.id(), foto2.id()));
    borradores.actualizar(borrador);

    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), null, null),
                        new FotoAprobada(deOtroLote.id(), null, null))));

    assertTrue(producto.imagenPrincipal().isPresent());
    assertEquals(1, producto.galeria().size(), "la sumada de otra publicación entra a la galería");
  }

  /** Una prenda es una variante y una variante tiene un color: sin él no hay qué ofrecer. */
  @Test
  void unaPrendaSinColorNoSeAprueba() {
    assertThrows(
        PrendaIncoherenteException.class,
        () ->
            caso()
                .ejecutar(
                    comando(
                        List.of(
                            new FotoAprobada(foto1.id(), "Rojo", "#C0392B", 1),
                            new FotoAprobada(foto2.id(), null, null, 2)))));
    assertTrue(productos.porId.isEmpty(), "ni producto a medias");
    assertTrue(almacenPublico.objetos.isEmpty(), "ni fotos en el bucket público");
    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());
  }

  /** Dos colores en una misma prenda no caben en una variante: se rechaza antes de subir nada. */
  @Test
  void unaPrendaConDosColoresNoSeAprueba() {
    assertThrows(
        PrendaIncoherenteException.class,
        () ->
            caso()
                .ejecutar(
                    comando(
                        List.of(
                            new FotoAprobada(foto1.id(), "Rojo", "#C0392B", 1),
                            new FotoAprobada(foto2.id(), "Blanco", "#FFFFFF", 1)))));
    assertTrue(productos.porId.isEmpty());
    assertTrue(almacenPublico.objetos.isEmpty());
  }

  @Test
  void lasPrendasSeNumeranDesdeUno() {
    assertThrows(
        IllegalArgumentException.class, () -> new FotoAprobada(foto1.id(), "Rojo", null, 0));
  }

  /** Un tono que sale una sola vez no se numera: el número solo aparece cuando hace falta. */
  @Test
  void unTonoQueNoSeRepiteSeQuedaConSuNombre() {
    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(foto1.id(), "Negro", "#000000"),
                        new FotoAprobada(foto2.id(), "Vino", null))));

    assertEquals("Negro", producto.variantes().get(0).atributos().get(0).valor());
    assertEquals("Vino", producto.variantes().get(1).atributos().get(0).valor());
  }

  @Test
  void sinFotosNoSeAprueba() {
    assertThrows(BorradorSinFotosException.class, () -> caso().ejecutar(comando(List.of())));
    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());
    assertTrue(productos.porId.isEmpty());
  }

  /**
   * La descripción es obligatoria desde el 3 de octubre de 2026: sin la del extractor ni la de la
   * aprobación no se publica nada, y no se sube ni una foto.
   */
  @Test
  void sinDescripcionNoSeAprueba() {
    borrador.editar(null, null, null, null, null, null, null, " ", null);
    borradores.actualizar(borrador);

    assertThrows(
        BorradorSinDescripcionException.class,
        () -> caso().ejecutar(comando(List.of(new FotoAprobada(foto1.id(), null, null)))));
    assertEquals(0, productos.porId.size());
    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());
  }

  @Test
  void laDescripcionDeLaAprobacionMandaSobreLaDelBorrador() {
    AprobarBorradorComando base = comando(List.of(new FotoAprobada(foto1.id(), null, null)));
    AprobarBorradorComando conDescripcion =
        new AprobarBorradorComando(
            base.borradorId(),
            base.titulo(),
            "  Bolso mediano con tira en cuero.  ",
            base.categoriaId(),
            base.marcaId(),
            base.precioVenta(),
            base.tallas(),
            base.existenciaInicial(),
            base.altEs(),
            base.altEn(),
            base.fotos());

    assertEquals("Bolso mediano con tira en cuero.", caso().ejecutar(conDescripcion).descripcion());
  }

  @Test
  void unaFotoQueNoEsDelBorradorSeRechaza() {
    assertThrows(
        FotoNoEsDelBorradorException.class,
        () ->
            caso().ejecutar(comando(List.of(new FotoAprobada(UUID.randomUUID(), "Negro", null)))));
  }

  /**
   * Una foto subida desde el panel se aprueba como las del proveedor: puede ser la principal, lleva
   * tono y se copia al bucket público desde su archivo.
   */
  @Test
  void unaFotoSubidaDesdeElPanelSeApruebaComoLasDelProveedor() {
    String referencia = "proveedores/" + proveedor.id() + "/borradores/" + borrador.id() + "/a.png";
    almacenPrivado.guardar(referencia, "image/png", "foto-subida".getBytes(StandardCharsets.UTF_8));
    FotoSubida subida = new FotoSubida(UUID.randomUUID(), referencia, AHORA);
    borrador.agregarFotoSubida(subida);
    borradores.actualizar(borrador);

    Producto producto =
        caso()
            .ejecutar(
                comando(
                    List.of(
                        new FotoAprobada(subida.id(), "Negro", "#000000"),
                        new FotoAprobada(foto2.id(), "Vino", null))));

    assertTrue(
        producto
            .imagenPrincipal()
            .orElseThrow()
            .url()
            .startsWith("https://publico.local/productos/" + producto.id() + "/principal-"));
    assertTrue(
        almacenPublico.objetos.values().stream()
            .anyMatch(b -> new String(b, StandardCharsets.UTF_8).equals("foto-subida")),
        "la subida se copió al bucket público");
    assertEquals(1, producto.galeria().size());
  }

  /** Lo que quien revisa sacó no vuelve a entrar por la aprobación. */
  @Test
  void unaFotoDescartadaNoSeApruebaAunqueSeaDeLaPublicacion() {
    borrador.descartarFoto(foto2.id());
    borradores.actualizar(borrador);

    assertThrows(
        FotoNoEsDelBorradorException.class,
        () ->
            caso()
                .ejecutar(
                    comando(
                        List.of(
                            new FotoAprobada(foto1.id(), null, null),
                            new FotoAprobada(foto2.id(), null, null)))));
    assertTrue(productos.porId.isEmpty());
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
                null,
                false,
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
                    "Bolso de dama mediano.",
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

  /**
   * El borrador de un mensaje con varios productos nace sin huella visual; la recibe de la foto que
   * la persona marcó como principal, no de la primera de la publicación.
   */
  @Test
  void laHuellaVisualSaleDeLaFotoQueSeMarcoComoPrincipal() {
    caso()
        .ejecutar(
            comando(
                List.of(
                    new FotoAprobada(foto2.id(), "Vino", null),
                    new FotoAprobada(foto1.id(), "Negro", null))));

    PHash esperado =
        new CalculadorDePHashPorContenido()
            .de("foto-vino".getBytes(StandardCharsets.UTF_8))
            .orElseThrow();
    assertEquals(
        Optional.of(esperado), borradores.buscarPorId(borrador.id()).orElseThrow().pHash());
  }
}
