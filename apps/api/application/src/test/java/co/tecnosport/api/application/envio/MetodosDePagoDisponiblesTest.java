package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetodosDePagoDisponiblesTest {

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final CriteriosContraentrega CRITERIOS_PERMISIVOS =
      new CriteriosContraentrega(true, Dinero.deCop(10_000_000), Set.of());

  private RepositorioProductosFalso productos;
  private RepositorioCoberturaContraentregaFalso cobertura;
  private RepositorioPedidosFalso pedidos;
  private Variante variante;

  private MetodosDePagoDisponibles crear(CriteriosContraentrega criterios) {
    productos = new RepositorioProductosFalso();
    cobertura = new RepositorioCoberturaContraentregaFalso();
    pedidos = new RepositorioPedidosFalso();
    publicarProductoConVariante();
    return new MetodosDePagoDisponibles(productos, cobertura, pedidos, criterios);
  }

  private void publicarProductoConVariante() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    Producto producto =
        Producto.crear(
            "Camiseta running Dry-Fit",
            new Slug("camiseta-running-dry-fit"),
            "Descripción",
            marca,
            categoria);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://cdn.tecnosport.co/img.jpg",
            "https://cdn.tecnosport.co/img.webp",
            800,
            600,
            1000,
            "hash",
            "alt es",
            "alt en"));
    variante =
        Variante.crear(
            new Sku("TS-CAM-AZ-M"),
            Dinero.deCop(50_000),
            new BigDecimal("0.19"),
            5,
            null,
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);
  }

  private MetodosDePagoDisponiblesComando comando(TipoEntrega tipoEntrega, Direccion direccion) {
    return new MetodosDePagoDisponiblesComando(
        List.of(new MetodosDePagoDisponiblesComando.LineaComando(variante.id(), 1)),
        "cliente@tecnosport.co",
        tipoEntrega,
        direccion);
  }

  @Test
  void todosLosMetodosDePagoEnLineaSiempreEstanDisponibles() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);

    Set<MetodoPago> disponibles = caso.ejecutar(comando(TipoEntrega.RETIRO_EN_PUNTO, null));

    assertTrue(disponibles.contains(MetodoPago.TARJETA));
    assertTrue(disponibles.contains(MetodoPago.PSE));
    assertTrue(disponibles.contains(MetodoPago.NEQUI));
    assertTrue(disponibles.contains(MetodoPago.BANCOLOMBIA));
    assertTrue(disponibles.contains(MetodoPago.ADDI));
    assertTrue(disponibles.contains(MetodoPago.TRANSFERENCIA_MANUAL));
  }

  @Test
  void contraentregaDisponibleSiLaCiudadEstaCubiertaYNoHayOtroImpedimento() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cobertura.conCiudadCubierta("05001");

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertTrue(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleEnRetiroEnPunto() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cobertura.conCiudadCubierta("05001");

    Set<MetodoPago> disponibles = caso.ejecutar(comando(TipoEntrega.RETIRO_EN_PUNTO, null));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiLaCiudadNoEstaCubierta() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiElCompradorTieneUnRechazoPrevio() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cobertura.conCiudadCubierta("05001");
    pedidos.conRechazoEnEntrega("cliente@tecnosport.co");

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiElTotalSuperaElMontoMaximo() {
    CriteriosContraentrega montoBajo =
        new CriteriosContraentrega(true, Dinero.deCop(10_000), Set.of());
    MetodosDePagoDisponibles caso = crear(montoBajo);
    cobertura.conCiudadCubierta("05001");

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiLaCategoriaDelCarritoEstaExcluida() {
    CriteriosContraentrega sinRopaYCalzado =
        new CriteriosContraentrega(
            true, Dinero.deCop(10_000_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO));
    MetodosDePagoDisponibles caso = crear(sinRopaYCalzado);
    cobertura.conCiudadCubierta("05001");

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }
}
