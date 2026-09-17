package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El piso del valor declarado ({@code adr/0035}). Lo que se prueba aquí no es la aritmética de un
 * máximo: es que un cable barato no vuelva a dejar sin envío a domicilio a un carrito entero.
 */
class ArmadorDeBultosTest {

  private static final Dinero MINIMO = Dinero.deCop(10_000);

  private static final Paquete PAQUETE_CABLE = new Paquete(90, 12, 10, 3);

  private RepositorioProductosFalso productos;
  private ArmadorDeBultos armador;

  @BeforeEach
  void prepararCatalogo() {
    productos = new RepositorioProductosFalso();
    armador = new ArmadorDeBultos(productos, MINIMO);
  }

  @Test
  void eleva_al_minimo_el_bulto_que_queda_por_debajo() {
    Variante cable = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(cable, 1)));

    assertEquals(MINIMO, bultos.getFirst().bulto().valorDeclarado());
  }

  @Test
  void respeta_el_valor_del_bulto_que_ya_llega_al_minimo() {
    Variante camiseta = catalogoCon(Dinero.deCop(50_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(camiseta, 1)));

    assertEquals(Dinero.deCop(50_000), bultos.getFirst().bulto().valorDeclarado());
  }

  @Test
  void el_valor_justo_en_el_minimo_no_se_toca() {
    Variante justo = catalogoCon(MINIMO);

    List<BultoDespachable> bultos = armador.armar(List.of(linea(justo, 1)));

    assertEquals(MINIMO, bultos.getFirst().bulto().valorDeclarado());
  }

  /**
   * El bulto es por unidad, así que el piso se paga tantas veces como unidades haya: tres cables de
   * 8.000 declaran 30.000 y no 24.000. Está decidido y escrito en {@code adr/0035}; esta prueba
   * existe para que cambiarlo tenga que ser deliberado.
   */
  @Test
  void cada_unidad_barata_declara_el_minimo_por_su_cuenta() {
    Variante cable = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(cable, 3)));

    assertEquals(3, bultos.size());
    assertEquals(
        List.of(MINIMO, MINIMO, MINIMO),
        bultos.stream().map(bulto -> bulto.bulto().valorDeclarado()).toList());
  }

  /**
   * El valor congelado del pedido también tiene piso. Es el camino de la emisión, que recotiza con
   * lo que el comprador pagó, y si el piso solo viviera en el camino del checkout la guía se caería
   * justo al despachar — con el pedido ya cobrado.
   */
  @Test
  void eleva_tambien_el_valor_congelado_que_llega_en_la_linea() {
    Variante cable = catalogoCon(Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        armador.armar(List.of(new LineaAEmpacar(cable.id(), 1, Dinero.deCop(7_500))));

    assertEquals(MINIMO, bultos.getFirst().bulto().valorDeclarado());
  }

  private static LineaAEmpacar linea(Variante variante, int cantidad) {
    return new LineaAEmpacar(variante.id(), cantidad, null);
  }

  private Variante catalogoCon(Dinero precio) {
    Producto producto =
        Producto.crear(
            "Cable USB-C trenzado",
            new Slug("cable-usb-c-trenzado"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear("Cables", new Slug("cables"), LineaCatalogo.TECNOLOGIA));
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
    Variante variante =
        Variante.crear(
            new Sku("TS-CAB-USBC-1M"),
            precio,
            new BigDecimal("0.19"),
            10,
            null,
            PAQUETE_CABLE,
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);
    return variante;
  }
}
