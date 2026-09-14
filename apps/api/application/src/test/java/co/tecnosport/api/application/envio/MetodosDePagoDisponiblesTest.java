package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetodosDePagoDisponiblesTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final CriteriosContraentrega CRITERIOS_PERMISIVOS =
      new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of());

  private RepositorioProductosFalso productos;
  private CotizadorEnvioFalso cotizador;
  private RepositorioPedidosFalso pedidos;
  private Variante variante;

  private MetodosDePagoDisponibles crear(CriteriosContraentrega criterios) {
    productos = new RepositorioProductosFalso();
    cotizador = new CotizadorEnvioFalso();
    pedidos = new RepositorioPedidosFalso();
    publicarProductoConVariante();
    return new MetodosDePagoDisponibles(
        productos, new CotizarEnvio(productos, cotizador, () -> AHORA), pedidos, criterios);
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
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en"));
    variante =
        Variante.crear(
            new Sku("TS-CAM-AZ-M"),
            Dinero.deCop(50_000),
            new BigDecimal("0.19"),
            5,
            null,
            new Paquete(180, 30, 25, 4),
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
  void contraentregaDisponibleSiAlgunaTarifaRecaudaYNoHayOtroImpedimento() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cotizador.conTarifaQueRecauda();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertTrue(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleEnRetiroEnPunto() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cotizador.conTarifaQueRecauda();

    Set<MetodoPago> disponibles = caso.ejecutar(comando(TipoEntrega.RETIRO_EN_PUNTO, null));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  /**
   * Ninguna transportadora recauda en ese destino. Lo dice la cotización pedida con recaudo al
   * volver sin tarifas, que es la única señal de cobertura que da Skydropx (adr/0023).
   */
  @Test
  void contraentregaNoDisponibleSiNingunaTarifaRecauda() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cotizador.sinTarifas();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  /**
   * Hay transportadora para el destino, pero ninguna cobra en la puerta. Antes esto era
   * indistinguible de "no hay envío"; ahora son dos respuestas distintas y solo una quita la
   * contraentrega.
   */
  @Test
  void contraentregaNoDisponibleSiLasTarifasNoRecaudan() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cotizador.conTarifaQueNoRecauda();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  /**
   * El transportador recauda el total, flete incluido (adr/0023), así que el tope se compara contra
   * eso. Con mercancía de 50.000 y flete de 14.900 el recaudo son 64.900: por debajo de un tope de
   * 60.000 si solo se mirara la mercancía, y por encima mirando lo que de verdad se cobra.
   */
  @Test
  void elTopeDelRecaudoCuentaTambienElFlete() {
    CriteriosContraentrega topeJusto =
        new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(60_000), Set.of());
    MetodosDePagoDisponibles caso = crear(topeJusto);
    cotizador.conTarifaQueRecauda();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiElCompradorTieneUnRechazoPrevio() {
    MetodosDePagoDisponibles caso = crear(CRITERIOS_PERMISIVOS);
    cotizador.conTarifaQueRecauda();
    pedidos.conRechazoEnEntrega("cliente@tecnosport.co");

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiElTotalSuperaElMontoMaximo() {
    CriteriosContraentrega montoBajo =
        new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(10_000), Set.of());
    MetodosDePagoDisponibles caso = crear(montoBajo);
    cotizador.conTarifaQueRecauda();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  @Test
  void contraentregaNoDisponibleSiLaCategoriaDelCarritoEstaExcluida() {
    CriteriosContraentrega sinRopaYCalzado =
        new CriteriosContraentrega(
            true, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO));
    MetodosDePagoDisponibles caso = crear(sinRopaYCalzado);
    cotizador.conTarifaQueRecauda();

    Set<MetodoPago> disponibles =
        caso.ejecutar(comando(TipoEntrega.ENVIO_A_DOMICILIO, DIRECCION_MEDELLIN));

    assertFalse(disponibles.contains(MetodoPago.CONTRAENTREGA));
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CotizadorEnvioFalso implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();

    /** Como responde Skydropx a una cotización pedida con recaudo: la tarifa lo admite. */
    void conTarifaQueRecauda() {
      this.tarifas = List.of(tarifa(true));
    }

    /** Hay transportadora, pero no cobra en la puerta. */
    void conTarifaQueNoRecauda() {
      this.tarifas = List.of(tarifa(false));
    }

    void sinTarifas() {
      this.tarifas = List.of();
    }

    private static TarifaEnvio tarifa(boolean admiteContraentrega) {
      return new TarifaEnvio(
          "rate_1",
          "Coordinadora",
          "Standard",
          Dinero.deCop(14_900),
          2,
          admiteContraentrega,
          AHORA.plusSeconds(3600));
    }

    @Override
    public List<TarifaEnvio> cotizar(CotizacionEnvio cotizacion) {
      return tarifas;
    }
  }
}
