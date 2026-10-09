package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.AtributoInvalidoException;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.ImagenProductoInvalidaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ProductoConColorException;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Un color nuevo para la foto principal, elegido desde la edición del producto. */
class AgregarColorDesdeLaPrincipalTest {

  private static final Atributo COLOR = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
  private static final Atributo TALLA = Atributo.crear("Talla", TipoAtributo.TEXTO, List.of());
  private static final Instant AHORA = Instant.parse("2026-10-08T00:00:00Z");

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioAtributosFalso atributos = new RepositorioAtributosFalso();
  private final RepositorioInventarioFalso inventario = new RepositorioInventarioFalso();
  private final RepositorioPaletaDeColores paleta =
      () ->
          List.of(
              new ColorDePaleta(UUID.randomUUID(), "Negro", "Black", "#111111", 1),
              new ColorDePaleta(UUID.randomUUID(), "Rojo", "Red", "#C62828", 2),
              new ColorDePaleta(UUID.randomUUID(), "Vino", "Burgundy", "#722F37", 3));
  private final AgregarColorDesdeLaPrincipal caso =
      new AgregarColorDesdeLaPrincipal(
          productos,
          atributos,
          paleta,
          new AgregarVariante(
              productos, atributos, inventario, new RelojFalso(AHORA), false, paleta));

  private final Producto producto =
      Producto.crear(
          "Conjunto deportivo",
          new Slug("conjunto-deportivo"),
          "",
          Marca.crear("Genérica"),
          Categoria.crear("Conjuntos", new Slug("conjuntos"), LineaCatalogo.ROPA));

  AgregarColorDesdeLaPrincipalTest() {
    atributos.conAtributos(TALLA, COLOR);
    productos.conProductos(producto);
    producto.asignarImagenPrincipal(principal());
  }

  /**
   * El caso que lo pidió: la galería es negra, la principal es roja. Rojo trae todas las tallas,
   * cada una con el precio y el empaque de la suya y las unidades que se dijeron, y la principal
   * queda colgada de la primera.
   */
  @Test
  void unColorNuevoCreaUnaVariantePorTallaYMarcaLaPrincipal() {
    Variante negroS = variante("PRV-1-S", "S-M", "Negro", 60_000, new Paquete(300, 30, 25, 4));
    Variante negroL = variante("PRV-1-L", "L-XL", "Negro", 65_000, null);
    producto.agregarVariante(negroS);
    producto.agregarVariante(negroL);

    ImagenProducto marcada =
        caso.ejecutar(
            new AgregarColorDesdeLaPrincipalComando(
                producto.id(),
                "Rojo",
                List.of(
                    new ExistenciaDelColorNuevo(negroS.id(), 3),
                    new ExistenciaDelColorNuevo(negroL.id(), 0))));

    assertEquals(4, producto.variantes().size());
    Variante rojoS = producto.variantes().get(2);
    Variante rojoL = producto.variantes().get(3);
    assertEquals(new Sku("PRV-1-S-ROJO"), rojoS.sku());
    assertEquals(new Sku("PRV-1-L-ROJO"), rojoL.sku());
    assertEquals("Rojo", rojoS.color().orElseThrow().valor());
    assertEquals("#C62828", rojoS.color().orElseThrow().colorHex());
    assertEquals("S-M", rojoS.atributosSinColor().get(0).valor());
    assertEquals("L-XL", rojoL.atributosSinColor().get(0).valor());
    assertEquals(Dinero.deCop(60_000), rojoS.precio());
    assertEquals(Dinero.deCop(65_000), rojoL.precio());
    assertEquals(Optional.of(new Paquete(300, 30, 25, 4)), rojoS.paquete());
    assertEquals(Optional.empty(), rojoL.paquete());
    assertEquals(
        3, inventario.buscarPorVarianteId(rojoS.id()).orElseThrow().saldoDisponible(AHORA));
    assertEquals(
        0, inventario.buscarPorVarianteId(rojoL.id()).orElseThrow().saldoDisponible(AHORA));

    assertEquals(Optional.of(rojoS.id()), marcada.varianteId());
    assertEquals(marcada.id(), productos.varianteGuardada.getKey());
    assertEquals(rojoS.id(), productos.varianteGuardada.getValue());
  }

  /** El borrador aprobado sin tono: no se duplica nada, las variantes que hay toman el color. */
  @Test
  void unProductoSinColorPintaSusVariantesSinCrearOtras() {
    Variante s = variante("PRV-2-S", "S-M", null, 60_000, null);
    Variante l = variante("PRV-2-L", "L-XL", null, 60_000, null);
    producto.agregarVariante(s);
    producto.agregarVariante(l);

    ImagenProducto marcada =
        caso.ejecutar(new AgregarColorDesdeLaPrincipalComando(producto.id(), "Negro / Vino", null));

    assertEquals(2, producto.variantes().size());
    assertEquals(List.of(s.id(), l.id()), List.copyOf(productos.atributosAgregados.keySet()));
    ValorAtributo color = productos.atributosAgregados.get(s.id());
    assertEquals("Negro / Vino", color.valor());
    assertEquals(2, color.muestra().partes().size());
    assertEquals(Optional.of(s.id()), marcada.varianteId());
  }

  @Test
  void unColorQueYaTieneSeEligeDeLosSuyosNoSeVuelveACrear() {
    Variante negro = variante("PRV-3-S", "S-M", "Negro", 60_000, null);
    producto.agregarVariante(negro);

    assertThrows(
        ProductoConColorException.class,
        () ->
            caso.ejecutar(
                new AgregarColorDesdeLaPrincipalComando(
                    producto.id(), "negro", List.of(new ExistenciaDelColorNuevo(negro.id(), 1)))));
    assertEquals(1, producto.variantes().size());
  }

  @Test
  void unColorFueraDeLaPaletaSeRechaza() {
    producto.agregarVariante(variante("PRV-4-S", "S-M", null, 60_000, null));

    assertThrows(
        AtributoInvalidoException.class,
        () ->
            caso.ejecutar(
                new AgregarColorDesdeLaPrincipalComando(producto.id(), "Turquesa", null)));
    assertTrue(productos.atributosAgregados.isEmpty());
  }

  /** Una talla sin existencia dejaría el color nuevo sin ella: no se crea ninguna. */
  @Test
  void faltaLaExistenciaDeUnaTallaYNoSeCreaNada() {
    Variante negroS = variante("PRV-5-S", "S-M", "Negro", 60_000, null);
    producto.agregarVariante(negroS);
    producto.agregarVariante(variante("PRV-5-L", "L-XL", "Negro", 60_000, null));

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            caso.ejecutar(
                new AgregarColorDesdeLaPrincipalComando(
                    producto.id(), "Rojo", List.of(new ExistenciaDelColorNuevo(negroS.id(), 2)))));
    assertEquals(2, producto.variantes().size());
  }

  @Test
  void unaExistenciaDeOtraVarianteSeRechaza() {
    Variante negroS = variante("PRV-6-S", "S-M", "Negro", 60_000, null);
    producto.agregarVariante(negroS);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            caso.ejecutar(
                new AgregarColorDesdeLaPrincipalComando(
                    producto.id(),
                    "Rojo",
                    List.of(
                        new ExistenciaDelColorNuevo(negroS.id(), 1),
                        new ExistenciaDelColorNuevo(UUID.randomUUID(), 1)))));
    assertEquals(1, producto.variantes().size());
  }

  /** El SKU que ya está tomado en otro producto no se repite: lleva un número detrás. */
  @Test
  void unSkuTomadoLlevaUnNumero() {
    Variante negro = variante("PRV-7-S", "S-M", "Negro", 60_000, null);
    producto.agregarVariante(negro);
    productos.conSkusEnUso("PRV-7-S-ROJO");

    caso.ejecutar(
        new AgregarColorDesdeLaPrincipalComando(
            producto.id(), "Rojo", List.of(new ExistenciaDelColorNuevo(negro.id(), 1))));

    assertEquals(new Sku("PRV-7-S-ROJO-2"), producto.variantes().get(1).sku());
  }

  @Test
  void sinFotoPrincipalNoHayAQuienDarleColor() {
    Producto sinPrincipal =
        Producto.crear(
            "Bolso",
            new Slug("bolso"),
            "",
            Marca.crear("Genérica"),
            Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS));
    sinPrincipal.agregarVariante(variante("PRV-8-U", "Única", null, 60_000, null));
    productos.conProductos(sinPrincipal);

    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            caso.ejecutar(
                new AgregarColorDesdeLaPrincipalComando(sinPrincipal.id(), "Rojo", null)));
  }

  private static Variante variante(
      String sku, String talla, String color, long precio, Paquete paquete) {
    List<ValorAtributo> valores =
        color == null
            ? List.of(ValorAtributo.de(TALLA, talla))
            : List.of(
                ValorAtributo.deColor(COLOR, color, "#111111"), ValorAtributo.de(TALLA, talla));
    return Variante.crear(
        new Sku(sku), Dinero.deCop(precio), BigDecimal.ZERO, null, paquete, valores);
  }

  private static ImagenProducto principal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(1200, "https://x/conjunto.jpg", 1000)),
        null,
        1200,
        new HashContenido("%064x".formatted(9)),
        "Conjunto",
        "Tracksuit");
  }
}
