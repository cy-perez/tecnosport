package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompraNoVigenteException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Generar, consultar, anular y listar los enlaces de compra (ADR-0086). */
class EnlaceDeCompraCasosDeUsoTest {

  private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");
  private static final Duration UN_DIA = Duration.ofHours(24);

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final RepositorioEnlacesDeCompraFalso enlaces = new RepositorioEnlacesDeCompraFalso();
  private final RelojFalso reloj = new RelojFalso(AHORA);

  private Variante publicar(boolean colorPorConfirmar, int existencia) {
    Producto producto =
        Producto.crear(
            "Apple iPhone 18 Pro",
            new Slug("apple-iphone-18-pro"),
            "Descripción",
            Marca.crear("Apple"),
            Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA));
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/i.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(7)),
            "alt es",
            "alt en"));
    Variante variante =
        new Variante(
            GeneradorIdentificador.nuevo(),
            new Sku("IPH18PRO-256-MORADO"),
            Dinero.deCop(6_000_000),
            BigDecimal.ZERO,
            null,
            null,
            EstadoVariante.ACTIVA,
            List.of(),
            null,
            false,
            colorPorConfirmar);
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);
    Inventario libro = Inventario.crear(variante.id());
    if (existencia > 0) {
      libro.registrarEntrada(existencia, "lista", AHORA);
    }
    inventarios.conInventario(libro);
    return variante;
  }

  private GenerarEnlaceDeCompra generar() {
    return new GenerarEnlaceDeCompra(productos, inventarios, enlaces, reloj, UN_DIA);
  }

  private int libres(Variante variante) {
    return inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoDisponible(AHORA);
  }

  @Test
  void generarApartaUnaUnidadPorLaVigenciaDelEnlace() {
    Variante variante = publicar(true, 2);

    EnlaceDeCompra enlace = generar().ejecutar(variante.id()).enlace();

    assertEquals(1, libres(variante));
    assertEquals(AHORA.plus(UN_DIA), enlace.expiraEn());
    assertEquals(
        2,
        inventarios
            .buscarPorVarianteId(variante.id())
            .orElseThrow()
            .saldoDisponible(AHORA.plus(UN_DIA)),
        "al vencer, la unidad vuelve sola");
  }

  @Test
  void sinUnidadLibreEntraLaQueElProveedorAcabaDeConfirmar() {
    Variante variante = publicar(true, 0);

    generar().ejecutar(variante.id());

    Inventario libro = inventarios.buscarPorVarianteId(variante.id()).orElseThrow();
    assertEquals(1, libro.saldoTotal());
    assertEquals(0, libro.saldoDisponible(AHORA));
  }

  @Test
  void unColorYaConfirmadoNoNecesitaEnlace() {
    Variante variante = publicar(false, 2);

    assertThrows(ExcepcionDeDominio.class, () -> generar().ejecutar(variante.id()));
    assertTrue(enlaces.porId.isEmpty());
    assertEquals(2, libres(variante));
  }

  @Test
  void consultarDevuelveElVigenteYNoElVencido() {
    Variante variante = publicar(true, 2);
    EnlaceDeCompra enlace = generar().ejecutar(variante.id()).enlace();

    assertEquals(
        variante.id(),
        new ConsultarEnlaceDeCompra(enlaces, reloj).ejecutar(enlace.token()).varianteId());
    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () ->
            new ConsultarEnlaceDeCompra(enlaces, new RelojFalso(AHORA.plus(UN_DIA)))
                .ejecutar(enlace.token()));
    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () -> new ConsultarEnlaceDeCompra(enlaces, reloj).ejecutar("inventado"));
  }

  @Test
  void anularDevuelveLaUnidadYElEnlaceDejaDeServir() {
    Variante variante = publicar(true, 2);
    EnlaceDeCompra enlace = generar().ejecutar(variante.id()).enlace();

    new AnularEnlaceDeCompra(enlaces, inventarios, reloj).ejecutar(enlace.id());

    assertEquals(2, libres(variante));
    assertFalse(enlaces.buscarPorId(enlace.id()).orElseThrow().vigente(AHORA));
  }

  @Test
  void anularSacaLaUnidadQueEntroConElEnlace() {
    Variante variante = publicar(true, 0);
    EnlaceDeCompra enlace = generar().ejecutar(variante.id()).enlace();

    new AnularEnlaceDeCompra(enlaces, inventarios, reloj).ejecutar(enlace.id());

    Inventario libro = inventarios.buscarPorVarianteId(variante.id()).orElseThrow();
    assertEquals(0, libro.saldoTotal(), "nadie más confirmó esa unidad");
    assertEquals(0, libro.saldoDisponible(AHORA));
  }

  @Test
  void unEnlaceYaUsadoNoSeAnulaNiTocaElLibro() {
    Variante variante = publicar(true, 2);
    EnlaceDeCompra enlace = generar().ejecutar(variante.id()).enlace();
    enlace.usar(java.util.UUID.randomUUID(), AHORA);
    enlaces.actualizar(enlace);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> new AnularEnlaceDeCompra(enlaces, inventarios, reloj).ejecutar(enlace.id()));
    assertEquals(1, libres(variante), "la reserva sigue siendo del pedido");
  }

  @Test
  void laBandejaDiceElEstadoDeCadaEnlace() {
    Variante variante = publicar(true, 3);
    EnlaceDeCompra anulado = generar().ejecutar(variante.id()).enlace();
    new AnularEnlaceDeCompra(enlaces, inventarios, reloj).ejecutar(anulado.id());
    EnlaceDeCompra vigente = generar().ejecutar(variante.id()).enlace();

    List<ListarEnlacesDeCompra.Fila> filas =
        new ListarEnlacesDeCompra(enlaces, productos, reloj).ejecutar();

    assertEquals(2, filas.size());
    assertEquals(
        ListarEnlacesDeCompra.Estado.VIGENTE,
        filas.stream().filter(f -> f.enlace().equals(vigente)).findFirst().orElseThrow().estado());
    assertEquals(
        ListarEnlacesDeCompra.Estado.ANULADO,
        filas.stream().filter(f -> f.enlace().equals(anulado)).findFirst().orElseThrow().estado());
  }
}
