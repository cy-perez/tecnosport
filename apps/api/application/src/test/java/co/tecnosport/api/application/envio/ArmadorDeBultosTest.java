package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Los dos extremos del rango asegurable, que se tratan distinto a propósito: el piso se eleva
 * ({@code adr/0035}) y el techo rechaza ({@code adr/0036}). Lo que se prueba aquí no es la
 * aritmética de un máximo y un mínimo: es que un cable barato no deje sin envío a un carrito
 * entero, y que un celular caro lo deje pero diciéndolo.
 */
class ArmadorDeBultosTest {

  private static final Dinero MINIMO = Dinero.deCop(10_000);
  private static final Dinero MAXIMO = Dinero.deCop(5_000_000);

  private static final Paquete PAQUETE_CABLE = new Paquete(90, 12, 10, 3);

  private RepositorioProductosFalso productos;
  private ArmadorDeBultos armador;
  private List<Producto> catalogo;

  @BeforeEach
  void prepararCatalogo() {
    productos = new RepositorioProductosFalso();
    catalogo = new ArrayList<>();
    armador = new ArmadorDeBultos(productos, MINIMO, MAXIMO);
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

  @Test
  void el_articulo_que_supera_el_maximo_no_se_puede_empacar() {
    Variante celular = catalogoCon(Dinero.deCop(8_000_000));

    ArticuloNoAsegurableException error =
        assertThrows(
            ArticuloNoAsegurableException.class, () -> armador.armar(List.of(linea(celular, 1))));

    assertEquals(1, error.articulos().size());
    assertEquals(celular.id(), error.articulos().getFirst().varianteId());
  }

  /**
   * El valor justo en el techo sí se despacha. Un límite que se equivoca por uno deja fuera al
   * artículo que costaba exactamente lo que la transportadora sí asegura, y nadie lo notaría: se
   * vería como "ese producto no se envía", que es lo que este caso viene a evitar.
   */
  @Test
  void el_valor_justo_en_el_maximo_todavia_se_despacha() {
    Variante justo = catalogoCon(MAXIMO);

    List<BultoDespachable> bultos = armador.armar(List.of(linea(justo, 1)));

    assertEquals(MAXIMO, bultos.getFirst().bulto().valorDeclarado());
  }

  /**
   * El techo se mira contra el valor de UNA unidad, que es lo que va en un bulto. Dos celulares de
   * tres millones son dos bultos de tres, y la plataforma valida por bulto (docs/13 §6.13). Si esto
   * mirara el total del carrito, un pedido grande de cosas baratas dejaría de despacharse sin
   * ninguna razón.
   */
  @Test
  void dos_unidades_que_suman_mas_que_el_maximo_si_se_despachan() {
    Variante celular = catalogoCon(Dinero.deCop(3_000_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(celular, 2)));

    assertEquals(2, bultos.size());
  }

  /**
   * Se nombran todos los culpables, no el primero: quitar un artículo del carrito y volver a chocar
   * con el siguiente es cómo se abandona una compra.
   */
  @Test
  void se_listan_todos_los_articulos_que_superan_el_maximo() {
    Variante celular = catalogoCon(Dinero.deCop(8_000_000));
    Variante portatil =
        agregarVarianteAlCatalogo("Portátil para diseño", "TS-PC-M4-16", Dinero.deCop(9_500_000));

    ArticuloNoAsegurableException error =
        assertThrows(
            ArticuloNoAsegurableException.class,
            () -> armador.armar(List.of(linea(celular, 1), linea(portatil, 1))));

    assertEquals(
        List.of(celular.id(), portatil.id()),
        error.articulos().stream()
            .map(ArticuloNoAsegurableException.Articulo::varianteId)
            .toList());
  }

  private static LineaAEmpacar linea(Variante variante, int cantidad) {
    return new LineaAEmpacar(variante.id(), cantidad, null);
  }

  /** El catálogo del caso típico: un solo producto, del precio que la prueba necesite. */
  private Variante catalogoCon(Dinero precio) {
    return agregarVarianteAlCatalogo("Cable USB-C trenzado", "TS-CAB-USBC-1M", precio);
  }

  /**
   * Acumula en el catálogo en vez de reemplazarlo: el doble de prueba guarda la última lista que le
   * pasan, así que dos llamadas seguidas dejarían vivo solo el segundo producto y la prueba de "se
   * listan todos" pasaría por la razón equivocada.
   */
  private Variante agregarVarianteAlCatalogo(String nombre, String sku, Dinero precio) {
    Producto producto =
        Producto.crear(
            nombre,
            new Slug(sku.toLowerCase(Locale.ROOT)),
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
            new Sku(sku), precio, new BigDecimal("0.19"), 10, null, PAQUETE_CABLE, List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    catalogo.add(producto);
    productos.conProductos(catalogo.toArray(new Producto[0]));
    return variante;
  }
}
