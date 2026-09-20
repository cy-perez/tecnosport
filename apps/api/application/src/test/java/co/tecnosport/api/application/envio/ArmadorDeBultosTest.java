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
   * El camino que abrió {@code adr/0046}: una variante sin medir no se puede empacar, pero no es un
   * error del sistema — el checkout lo traduce a recogida en el punto.
   */
  @Test
  void el_articulo_sin_medidas_no_se_puede_empacar() {
    Variante sinMedir =
        agregarVarianteAlCatalogo(
            "Parlante sin medir", "TS-SIN-MEDIR", Dinero.deCop(200_000), null);

    ArticuloSinMedidasException error =
        assertThrows(
            ArticuloSinMedidasException.class, () -> armador.armar(List.of(linea(sinMedir, 1))));

    assertEquals(1, error.articulos().size());
    assertEquals(sinMedir.id(), error.articulos().getFirst().varianteId());
    assertEquals("Parlante sin medir", error.articulos().getFirst().nombre());
  }

  /**
   * Con los dos motivos a la vez gana el techo asegurable, y no es un capricho de orden: de los dos
   * ese es el que no se arregla nunca. Decirle al comprador "nos falta medirlo" cuando además el
   * artículo jamás va a poder viajar asegurado sería darle una esperanza falsa.
   */
  @Test
  void si_un_articulo_no_es_asegurable_y_ademas_no_esta_medido_manda_el_techo() {
    Variante caroYSinMedir =
        agregarVarianteAlCatalogo("Proyector caro", "TS-CARO", Dinero.deCop(8_000_000), null);

    assertThrows(
        ArticuloNoAsegurableException.class, () -> armador.armar(List.of(linea(caroYSinMedir, 1))));
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
    return agregarVarianteAlCatalogo(nombre, sku, precio, PAQUETE_CABLE);
  }

  private Variante agregarVarianteAlCatalogo(
      String nombre, String sku, Dinero precio, Paquete paquete) {
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
        Variante.crear(new Sku(sku), precio, new BigDecimal("0.19"), 10, null, paquete, List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    catalogo.add(producto);
    productos.conProductos(catalogo.toArray(new Producto[0]));
    return variante;
  }

  // --- el recaudo de la contraentrega (adr/0037) ------------------------------------------------

  private static List<Dinero> declarados(List<BultoDespachable> bultos) {
    return bultos.stream().map(bulto -> bulto.bulto().valorDeclarado()).toList();
  }

  private static Dinero suma(List<BultoDespachable> bultos) {
    return Dinero.deCop(
        declarados(bultos).stream().map(Dinero::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /**
   * Lo que la prueba cuida no es el reparto: es que la transportadora cobre en la puerta
   * exactamente lo que el pedido dice. La plataforma no tiene campo para ese monto y lo calcula
   * sumando lo declarado, así que la suma <em>es</em> el recaudo.
   */
  @Test
  void la_suma_declarada_es_exactamente_lo_que_se_recauda() {
    Variante camiseta = catalogoCon(Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        armador.armarParaRecaudo(List.of(linea(camiseta, 1)), Dinero.deCop(58_200));

    assertEquals(Dinero.deCop(58_200), suma(bultos));
  }

  @Test
  void el_flete_se_reparte_proporcional_al_valor_de_cada_bulto() {
    Variante camiseta = catalogoCon(Dinero.deCop(30_000));
    Variante celular = catalogoCon(Dinero.deCop(90_000));

    List<BultoDespachable> bultos =
        armador.armarParaRecaudo(
            List.of(linea(camiseta, 1), linea(celular, 1)), Dinero.deCop(128_000));

    // 8.000 de flete sobre 120.000 declarados: una cuarta parte al de 30.000 y tres al de 90.000.
    assertEquals(List.of(Dinero.deCop(32_000), Dinero.deCop(96_000)), declarados(bultos));
  }

  /**
   * El residuo de las divisiones enteras va al bulto de mayor valor. Sin él la suma quedaría unos
   * pesos por debajo y la transportadora cobraría de menos en cada pedido: poco, y siempre.
   */
  @Test
  void el_residuo_de_la_division_no_se_pierde() {
    Variante camiseta = catalogoCon(Dinero.deCop(30_000));
    Variante celular = catalogoCon(Dinero.deCop(90_000));

    List<BultoDespachable> bultos =
        armador.armarParaRecaudo(
            List.of(linea(camiseta, 1), linea(celular, 1)), Dinero.deCop(127_851));

    assertEquals(Dinero.deCop(127_851), suma(bultos), "la suma cuadra al peso");
    assertEquals(Dinero.deCop(31_962), declarados(bultos).get(0));
    assertEquals(Dinero.deCop(95_889), declarados(bultos).get(1), "el residuo va al mayor");
  }

  /**
   * El caso que obliga a no ofrecer contraentrega: el piso del {@code adr/0035} ya infló la suma
   * por encima de lo que el pedido cobra. Diez cables de 8.000 declaran 100.000 contra 80.000 de
   * mercancía, y ningún flete nacional cierra esos 20.000.
   */
  @Test
  void si_el_piso_ya_supera_el_total_ese_carrito_no_lleva_contraentrega() {
    Variante cable = catalogoCon(Dinero.deCop(8_000));

    RecaudoNoCuadraException error =
        assertThrows(
            RecaudoNoCuadraException.class,
            () -> armador.armarParaRecaudo(List.of(linea(cable, 10)), Dinero.deCop(88_000)));

    assertEquals(Dinero.deCop(100_000), error.declarado());
    assertEquals(Dinero.deCop(88_000), error.aRecaudar());
  }

  /** Justo en el filo: declarar exactamente lo que se cobra sí cuadra, no hay nada que repartir. */
  @Test
  void declarar_exactamente_el_total_cuadra() {
    Variante cable = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos =
        armador.armarParaRecaudo(List.of(linea(cable, 2)), Dinero.deCop(20_000));

    assertEquals(List.of(MINIMO, MINIMO), declarados(bultos));
  }

  /**
   * El techo se valida DESPUÉS de repartir el flete, porque el flete es parte de lo declarado y por
   * tanto de lo que la plataforma valida. Consecuencia buscada y anotada en el ADR: el mismo
   * artículo puede ser asegurable pagando en línea y no pagando contraentrega.
   */
  @Test
  void un_articulo_al_filo_del_techo_se_pasa_solo_en_contraentrega() {
    Variante celular = catalogoCon(Dinero.deCop(4_999_000));

    assertEquals(1, armador.armar(List.of(linea(celular, 1))).size(), "en línea sí va");
    assertThrows(
        ArticuloNoAsegurableException.class,
        () -> armador.armarParaRecaudo(List.of(linea(celular, 1)), Dinero.deCop(5_007_000)));
  }

  /** Un pedido pagado en línea no cambia: el declarado sigue siendo el de la mercancía. */
  @Test
  void el_camino_en_linea_no_reparte_nada() {
    Variante camiseta = catalogoCon(Dinero.deCop(50_000));

    assertEquals(
        List.of(Dinero.deCop(50_000)), declarados(armador.armar(List.of(linea(camiseta, 1)))));
  }
}
