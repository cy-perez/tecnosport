package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lo que un producto de proveedor tiene de más, y lo que un manual no tiene. */
class ProductoDeProveedorTest {

  private static final Instant VISTO = Instant.parse("2026-09-28T15:15:00Z");
  private static final Instant DESPUES = Instant.parse("2026-09-30T10:00:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final Marca MARCA = new Marca(UUID.randomUUID(), "Genérica");
  private static final Categoria CATEGORIA =
      Categoria.crear(
          "Bolsos de mano", new Slug("bolsos-dama-bolsos-de-mano"), LineaCatalogo.BOLSOS);
  private static final HuellaProveedor HUELLA =
      HuellaProveedor.calcular(PROVEEDOR, "Bolso de dama mediano", Dinero.deCop(53000));

  private static Producto deProveedor() {
    return Producto.crearDeProveedor(
        "Bolso de dama mediano",
        new Slug("bolso-de-dama-mediano"),
        "Dos compartimientos.",
        MARCA,
        CATEGORIA,
        PROVEEDOR,
        Dinero.deCop(53000),
        HUELLA,
        VISTO);
  }

  private static Producto manual() {
    return Producto.crear("Camiseta", new Slug("camiseta"), "", MARCA, CATEGORIA);
  }

  private static ImagenProducto principal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(1200, "https://b/p.jpg", 900)),
        null,
        1200,
        new HashContenido("%064x".formatted(1)),
        "Bolso",
        "Bag");
  }

  @Test
  void naceEnBorradorDisponibleYVistoEnLaFechaDelMensaje() {
    Producto producto = deProveedor();

    assertEquals(OrigenProducto.PROVEEDOR, producto.origen());
    assertEquals(EstadoProducto.BORRADOR, producto.estado());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    assertEquals(Optional.of(PROVEEDOR), producto.proveedorId());
    assertEquals(Optional.of(HUELLA), producto.huellaProveedor());
    assertEquals(Optional.of(VISTO), producto.vistoPorUltimaVez());
    assertEquals(Optional.of(Dinero.deCop(53000)), producto.precioProveedor());
    assertFalse(producto.estaEnVitrina(), "en borrador no está en vitrina");
  }

  @Test
  void unManualNoLlevaNadaDeProveedorYElJobNoLoToca() {
    Producto producto = manual();

    assertEquals(OrigenProducto.MANUAL, producto.origen());
    assertEquals(Optional.empty(), producto.proveedorId());
    assertEquals(Optional.empty(), producto.vistoPorUltimaVez());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    assertThrows(ExcepcionDeDominio.class, producto::ocultarPorVencimiento);
    assertThrows(ExcepcionDeDominio.class, () -> producto.marcarAgotadoPorProveedor(VISTO));
    assertThrows(ExcepcionDeDominio.class, () -> producto.renovar(VISTO));
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
  }

  @Test
  void estaEnVitrinaSoloPublicadoYDisponible() {
    Producto producto = deProveedor();
    producto.asignarImagenPrincipal(principal());
    producto.publicar();
    assertTrue(producto.estaEnVitrina());

    producto.ocultarPorVencimiento();
    assertEquals(EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO, producto.estadoDisponibilidad());
    assertEquals(EstadoProducto.PUBLICADO, producto.estado(), "ocultar no despublica");
    assertFalse(producto.estaEnVitrina());

    producto.renovar(DESPUES);
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    assertEquals(Optional.of(DESPUES), producto.vistoPorUltimaVez());
    assertTrue(producto.estaEnVitrina());
  }

  /** Un mensaje más viejo que el último visto no retrocede la fecha. */
  @Test
  void renovarNoRetrocedeLaUltimaVista() {
    Producto producto = deProveedor();
    producto.renovar(DESPUES);

    producto.renovar(VISTO);

    assertEquals(Optional.of(DESPUES), producto.vistoPorUltimaVez());
  }

  /**
   * Dos lotes procesados fuera de orden: el "agotado" de hoy no lo pisa una renovación con fecha de
   * anteayer, ni al revés.
   */
  @Test
  void unAvisoMasViejoQueElUltimoVistoNoCambiaElEstado() {
    Producto producto = deProveedor();
    producto.marcarAgotadoPorProveedor(DESPUES);

    producto.renovar(VISTO);
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, producto.estadoDisponibilidad());

    producto.renovar(DESPUES.plusSeconds(60));
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    producto.marcarAgotadoPorProveedor(VISTO);
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
  }

  /**
   * La huella lleva el precio dentro: con el precio viejo, el siguiente anuncio no la encuentra.
   */
  @Test
  void cambiarElPrecioDeProveedorRecalculaLaHuella() {
    Producto producto = deProveedor();

    producto.actualizarPrecioProveedor(Dinero.deCop(60000));

    assertEquals(
        Optional.of(
            HuellaProveedor.calcular(PROVEEDOR, "Bolso de dama mediano", Dinero.deCop(60000))),
        producto.huellaProveedor());
  }

  @Test
  void agotadoPorProveedorEsInmediatoYSeReactivaConUnaRenovacion() {
    Producto producto = deProveedor();

    producto.marcarAgotadoPorProveedor(DESPUES);
    assertEquals(EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR, producto.estadoDisponibilidad());
    assertEquals(Optional.of(DESPUES), producto.vistoPorUltimaVez());

    producto.ocultarPorVencimiento();
    assertEquals(
        EstadoDisponibilidad.AGOTADO_POR_PROVEEDOR,
        producto.estadoDisponibilidad(),
        "el vencimiento no pisa un agotado");

    producto.renovar(DESPUES.plusSeconds(60));
    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
  }

  @Test
  void ocultarEsIdempotente() {
    Producto producto = deProveedor();
    producto.ocultarPorVencimiento();
    producto.ocultarPorVencimiento();

    assertEquals(EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO, producto.estadoDisponibilidad());
  }

  @Test
  void elPrecioDelProveedorSePuedeActualizar() {
    Producto producto = deProveedor();

    producto.actualizarPrecioProveedor(Dinero.deCop(55000));

    assertEquals(Optional.of(Dinero.deCop(55000)), producto.precioProveedor());
    assertThrows(
        ExcepcionDeDominio.class, () -> manual().actualizarPrecioProveedor(Dinero.deCop(1)));
  }

  @Test
  void unProveedorSinHuellaOUnManualConProveedorNoSeSostienen() {
    assertThrows(
        NullPointerException.class,
        () ->
            new Producto(
                UUID.randomUUID(),
                "x",
                new Slug("x"),
                "",
                MARCA,
                CATEGORIA,
                EstadoProducto.BORRADOR,
                null,
                List.of(),
                null,
                List.of(),
                OrigenProducto.PROVEEDOR,
                PROVEEDOR,
                null,
                null,
                VISTO,
                EstadoDisponibilidad.DISPONIBLE));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new Producto(
                UUID.randomUUID(),
                "x",
                new Slug("x"),
                "",
                MARCA,
                CATEGORIA,
                EstadoProducto.BORRADOR,
                null,
                List.of(),
                null,
                List.of(),
                OrigenProducto.MANUAL,
                PROVEEDOR,
                null,
                null,
                null,
                EstadoDisponibilidad.DISPONIBLE));
  }
}
