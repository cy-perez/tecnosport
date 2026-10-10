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
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
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
 * aritmética de un máximo y un mínimo: es que unos audífonos baratos no dejen sin envío a un
 * carrito entero, y que un celular caro lo deje pero diciéndolo.
 */
class ArmadorDeBultosTest {

  private static final Dinero MINIMO = Dinero.deCop(10_000);
  private static final Dinero MAXIMO = Dinero.deCop(5_000_000);

  private static final Paquete PAQUETE_AUDIFONOS = new Paquete(90, 12, 10, 3);

  private RepositorioProductosFalso productos;
  private ArmadorDeBultos armador;
  private List<Producto> catalogo;

  @BeforeEach
  void prepararCatalogo() {
    productos = new RepositorioProductosFalso();
    catalogo = new ArrayList<>();
    armador = ArmadorDeBultos.sinPromedios(productos, MINIMO, MAXIMO);
  }

  @Test
  void eleva_al_minimo_el_bulto_que_queda_por_debajo() {
    Variante audifonos = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(audifonos, 1)));

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
   * El bulto es por unidad, así que el piso se paga tantas veces como unidades haya: tres audífonos
   * de 8.000 declaran 30.000 y no 24.000. Está decidido y escrito en {@code adr/0035}; esta prueba
   * existe para que cambiarlo tenga que ser deliberado.
   */
  @Test
  void cada_unidad_barata_declara_el_minimo_por_su_cuenta() {
    Variante audifonos = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos = armador.armar(List.of(linea(audifonos, 3)));

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
    Variante audifonos = catalogoCon(Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        armador.armar(List.of(new LineaAEmpacar(audifonos.id(), 1, Dinero.deCop(7_500))));

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
    return agregarVarianteAlCatalogo("Audífonos in-ear básicos", "TS-AUD-INEAR-1", precio);
  }

  /**
   * Acumula en el catálogo en vez de reemplazarlo: el doble de prueba guarda la última lista que le
   * pasan, así que dos llamadas seguidas dejarían vivo solo el segundo producto y la prueba de "se
   * listan todos" pasaría por la razón equivocada.
   */
  private Variante agregarVarianteAlCatalogo(String nombre, String sku, Dinero precio) {
    return agregarVarianteAlCatalogo(nombre, sku, precio, PAQUETE_AUDIFONOS);
  }

  private Variante agregarVarianteAlCatalogo(
      String nombre, String sku, Dinero precio, Paquete paquete) {
    return agregarVarianteAlCatalogo(
        nombre,
        sku,
        precio,
        paquete,
        Categoria.crear("Audífonos", new Slug("audifonos"), LineaCatalogo.TECNOLOGIA));
  }

  private Variante agregarVarianteAlCatalogo(
      String nombre, String sku, Dinero precio, Paquete paquete, Categoria categoria) {
    Producto producto =
        Producto.crear(
            nombre,
            new Slug(sku.toLowerCase(Locale.ROOT)),
            "Descripción",
            Marca.crear("TecnoSport"),
            categoria);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/img.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en"));
    Variante variante =
        Variante.crear(new Sku(sku), precio, new BigDecimal("0.19"), null, paquete, List.of());
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
   * por encima de lo que el pedido cobra. Diez audífonos de 8.000 declaran 100.000 contra 80.000 de
   * mercancía, y ningún flete nacional cierra esos 20.000.
   */
  @Test
  void si_el_piso_ya_supera_el_total_ese_carrito_no_lleva_contraentrega() {
    Variante audifonos = catalogoCon(Dinero.deCop(8_000));

    RecaudoNoCuadraException error =
        assertThrows(
            RecaudoNoCuadraException.class,
            () -> armador.armarParaRecaudo(List.of(linea(audifonos, 10)), Dinero.deCop(88_000)));

    assertEquals(Dinero.deCop(100_000), error.declarado());
    assertEquals(Dinero.deCop(88_000), error.aRecaudar());
  }

  /** Justo en el filo: declarar exactamente lo que se cobra sí cuadra, no hay nada que repartir. */
  @Test
  void declarar_exactamente_el_total_cuadra() {
    Variante audifonos = catalogoCon(Dinero.deCop(8_000));

    List<BultoDespachable> bultos =
        armador.armarParaRecaudo(List.of(linea(audifonos, 2)), Dinero.deCop(20_000));

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

  // --- el peso redondeado y la bolsa de referencia (adr/0071) -----------------------------------

  private static final Categoria CAMISETAS =
      Categoria.crear("Camisetas", new Slug("ropa-caballero-camisetas"), LineaCatalogo.ROPA);
  private static final Categoria JEANS =
      Categoria.crear("Jeans", new Slug("ropa-dama-jeans"), LineaCatalogo.ROPA);
  private static final Categoria TENIS =
      Categoria.crear("Unisex", new Slug("calzado-unisex"), LineaCatalogo.CALZADO);
  private static final Categoria PARLANTES =
      Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);

  private RepositorioReferenciasDeEnvioFalso referencias;

  /** Las cifras que dio el negocio el 7 de octubre de 2026: bolsa de 40 × 30 × 10 cm. */
  private ArmadorDeBultos armadorConReferencias() {
    referencias =
        new RepositorioReferenciasDeEnvioFalso()
            .conMedidas(40, 30, 10)
            .conPeso(CAMISETAS.id(), 300)
            .conPeso(JEANS.id(), 700)
            .conPeso(TENIS.id(), 700);
    return new ArmadorDeBultos(productos, referencias, MINIMO, MAXIMO);
  }

  private Variante prenda(Categoria categoria, String sku, Dinero precio) {
    return agregarVarianteAlCatalogo(sku, sku, precio, null, categoria);
  }

  /** El formulario de la plataforma pide kilos enteros: 90 gramos de audífonos son 1 kg. */
  @Test
  void el_peso_de_un_bulto_medido_sube_al_kilo_entero() {
    Variante audifonos = catalogoCon(Dinero.deCop(50_000));

    Paquete paquete = armador.armar(List.of(linea(audifonos, 1))).getFirst().bulto().paquete();

    assertEquals(new Paquete(1000, 12, 10, 3), paquete);
  }

  @Test
  void una_prenda_sin_medir_viaja_en_la_bolsa_de_referencia() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    List<BultoDespachable> bultos = conReferencias.armar(List.of(linea(camiseta, 1)));

    assertEquals(1, bultos.size());
    assertEquals(new Paquete(1000, 40, 30, 10), bultos.getFirst().bulto().paquete());
    assertEquals(Dinero.deCop(50_000), bultos.getFirst().bulto().valorDeclarado());
    assertEquals("Prendas de vestir", bultos.getFirst().contenido());
  }

  /**
   * Se suma y <em>después</em> se redondea: dos prendas de 700 g son 1.400 g y una bolsa de 2 kg,
   * no dos kilos por prenda. Y todo va en una sola bolsa, que declara la suma de lo que lleva.
   */
  @Test
  void varias_prendas_van_en_una_sola_bolsa_con_el_peso_sumado() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante jean = prenda(JEANS, "TS-JEAN-1", Dinero.deCop(90_000));
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        conReferencias.armar(List.of(linea(jean, 1), linea(camiseta, 2)));

    assertEquals(1, bultos.size());
    assertEquals(2000, bultos.getFirst().bulto().paquete().pesoGramos());
    assertEquals(Dinero.deCop(190_000), bultos.getFirst().bulto().valorDeclarado());
  }

  @Test
  void una_suma_que_cae_justo_en_el_kilo_no_sube_al_siguiente() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante jean = prenda(JEANS, "TS-JEAN-1", Dinero.deCop(90_000));
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        conReferencias.armar(List.of(linea(jean, 1), linea(camiseta, 1)));

    assertEquals(1000, bultos.getFirst().bulto().paquete().pesoGramos());
  }

  /** Lo que el negocio llena en la plataforma con un pedido mixto: la caja medida y la bolsa. */
  @Test
  void un_pedido_mixto_son_dos_bultos_y_la_bolsa_va_al_final() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante tenis = prenda(TENIS, "TS-TEN-1", Dinero.deCop(250_000));
    Variante parlante =
        agregarVarianteAlCatalogo(
            "Parlante",
            "TS-PAR-1",
            Dinero.deCop(400_000),
            new Paquete(1_500, 25, 20, 15),
            PARLANTES);

    List<BultoDespachable> bultos =
        conReferencias.armar(List.of(linea(tenis, 1), linea(parlante, 1)));

    assertEquals(2, bultos.size());
    assertEquals(new Paquete(2000, 25, 20, 15), bultos.get(0).bulto().paquete());
    assertEquals("Electrónica y accesorios", bultos.get(0).contenido());
    assertEquals(new Paquete(1000, 40, 30, 10), bultos.get(1).bulto().paquete());
    assertEquals("Calzado deportivo", bultos.get(1).contenido());
  }

  /** La medida real manda: una prenda que alguien pesó no viaja con el promedio de su categoría. */
  @Test
  void una_prenda_medida_viaja_con_sus_medidas_y_fuera_de_la_bolsa() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante medida =
        agregarVarianteAlCatalogo(
            "Jean medido", "TS-JEAN-M", Dinero.deCop(90_000), new Paquete(650, 35, 25, 5), JEANS);
    Variante sinMedir = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    List<BultoDespachable> bultos =
        conReferencias.armar(List.of(linea(medida, 1), linea(sinMedir, 1)));

    assertEquals(2, bultos.size());
    assertEquals(new Paquete(1000, 35, 25, 5), bultos.get(0).bulto().paquete());
    assertEquals(new Paquete(1000, 40, 30, 10), bultos.get(1).bulto().paquete());
  }

  /** Una bolsa con dos líneas las declara las dos: declarar una dejaría la otra sin reclamación. */
  @Test
  void la_bolsa_declara_cada_linea_que_lleva() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));
    Variante tenis = prenda(TENIS, "TS-TEN-1", Dinero.deCop(250_000));

    List<BultoDespachable> bultos =
        conReferencias.armar(List.of(linea(camiseta, 1), linea(tenis, 1)));

    assertEquals("Prendas de vestir, Calzado deportivo", bultos.getFirst().contenido());
  }

  /** El piso es por bulto: dos prendas de 6.000 declaran 12.000, no dos veces el mínimo. */
  @Test
  void el_piso_de_la_bolsa_se_mira_sobre_la_suma() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante barata = prenda(CAMISETAS, "TS-CAM-B", Dinero.deCop(6_000));

    assertEquals(
        List.of(Dinero.deCop(12_000)), declarados(conReferencias.armar(List.of(linea(barata, 2)))));
    assertEquals(List.of(MINIMO), declarados(conReferencias.armar(List.of(linea(barata, 1)))));
  }

  /** Una categoría a la que nadie le puso peso sigue el adr/0046: solo recogida. */
  @Test
  void una_prenda_de_una_categoria_sin_peso_no_se_puede_empacar() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Categoria faldas = Categoria.crear("Faldas", new Slug("ropa-dama-faldas"), LineaCatalogo.ROPA);
    Variante falda = prenda(faldas, "TS-FAL-1", Dinero.deCop(60_000));

    ArticuloSinMedidasException error =
        assertThrows(
            ArticuloSinMedidasException.class,
            () -> conReferencias.armar(List.of(linea(falda, 1))));

    assertEquals(falda.id(), error.articulos().getFirst().varianteId());
  }

  /** Sin medidas de la bolsa no hay bolsa, aunque cada prenda tenga su peso. */
  @Test
  void sin_medidas_de_referencia_ninguna_prenda_sin_medir_se_empaca() {
    referencias = new RepositorioReferenciasDeEnvioFalso().conPeso(CAMISETAS.id(), 300);
    ArmadorDeBultos sinMedidas = new ArmadorDeBultos(productos, referencias, MINIMO, MAXIMO);
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    assertThrows(
        ArticuloSinMedidasException.class, () -> sinMedidas.armar(List.of(linea(camiseta, 1))));
  }

  /**
   * La tecnología no se promedia aunque alguien haya dejado un peso en su categoría: el panel no lo
   * permite, pero la regla tiene que sostenerse también aquí.
   */
  @Test
  void la_tecnologia_sin_medir_no_entra_en_la_bolsa_aunque_tenga_peso() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    referencias.conPeso(PARLANTES.id(), 900);
    Variante parlante =
        agregarVarianteAlCatalogo("Parlante", "TS-PAR-1", Dinero.deCop(400_000), null, PARLANTES);

    assertThrows(
        ArticuloSinMedidasException.class, () -> conReferencias.armar(List.of(linea(parlante, 1))));
  }

  /** Un carrito de puros productos medidos no va a la base a preguntar cuánto pesa una camiseta. */
  @Test
  void si_todo_esta_medido_las_referencias_no_se_leen() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante audifonos = catalogoCon(Dinero.deCop(50_000));

    conReferencias.armar(List.of(linea(audifonos, 2)));

    assertEquals(0, referencias.lecturas());
  }

  /** La bolsa entra al reparto del flete como un bulto más, y la suma sigue siendo el recaudo. */
  @Test
  void con_bolsa_la_suma_declarada_sigue_siendo_lo_que_se_recauda() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));
    Variante audifonos = catalogoCon(Dinero.deCop(30_000));

    List<BultoDespachable> bultos =
        conReferencias.armarParaRecaudo(
            List.of(linea(camiseta, 2), linea(audifonos, 1)), Dinero.deCop(143_700));

    assertEquals(2, bultos.size());
    assertEquals(Dinero.deCop(143_700), suma(bultos));
  }

  /**
   * El techo es por bulto, y antes de la bolsa doce pares de 450.000 eran doce bultos asegurables.
   * Metidos en una sola bolsa serían 5.400.000 y el pedido quedaría solo con recogida: la bolsa se
   * parte para que ninguna pase del techo.
   */
  @Test
  void una_bolsa_que_pasaria_del_techo_se_parte_en_varias() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante tenis = prenda(TENIS, "TS-TEN-1", Dinero.deCop(450_000));

    List<BultoDespachable> bultos = conReferencias.armar(List.of(linea(tenis, 12)));

    assertEquals(2, bultos.size());
    assertEquals(List.of(Dinero.deCop(4_950_000), Dinero.deCop(450_000)), declarados(bultos));
    assertEquals(8000, bultos.get(0).bulto().paquete().pesoGramos(), "11 pares de 700 g");
    assertEquals(1000, bultos.get(1).bulto().paquete().pesoGramos());
  }

  @Test
  void con_las_bolsas_partidas_el_recaudo_sigue_cuadrando() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante tenis = prenda(TENIS, "TS-TEN-1", Dinero.deCop(450_000));

    List<BultoDespachable> bultos =
        conReferencias.armarParaRecaudo(List.of(linea(tenis, 12)), Dinero.deCop(5_430_000));

    assertEquals(Dinero.deCop(5_430_000), suma(bultos));
  }

  /**
   * Una prenda que sola ya pasa del techo no se esconde dentro de la bolsa: el techo la rechaza.
   */
  @Test
  void una_prenda_que_sola_pasa_del_techo_no_se_despacha() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante cara = prenda(JEANS, "TS-JEAN-ORO", Dinero.deCop(6_000_000));
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));

    ArticuloNoAsegurableException error =
        assertThrows(
            ArticuloNoAsegurableException.class,
            () -> conReferencias.armar(List.of(linea(camiseta, 1), linea(cara, 1))));

    assertEquals(
        List.of(cara.id()),
        error.articulos().stream()
            .map(ArticuloNoAsegurableException.Articulo::varianteId)
            .toList());
  }

  /**
   * El carrito público no limita la cantidad: la suma de los pesos no puede dar la vuelta a un
   * número negativo y reventar con un error que no explica nada.
   */
  @Test
  void una_cantidad_enorme_no_desborda_el_peso_de_la_bolsa() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante barata = prenda(JEANS, "TS-JEAN-B", Dinero.deCop(1));

    List<BultoDespachable> bultos = conReferencias.armar(List.of(linea(barata, 3_100_000)));

    assertEquals(1, bultos.size());
    assertEquals(2_147_483_000, bultos.getFirst().bulto().paquete().pesoGramos());
  }

  /**
   * Lo que una bolsa con tecnología sin medir no puede hacer es esconderla: todo sale a recogida.
   */
  @Test
  void la_bolsa_junto_a_tecnologia_sin_medir_no_se_despacha() {
    ArmadorDeBultos conReferencias = armadorConReferencias();
    Variante camiseta = prenda(CAMISETAS, "TS-CAM-1", Dinero.deCop(50_000));
    Variante parlante =
        agregarVarianteAlCatalogo("Parlante", "TS-PAR-1", Dinero.deCop(400_000), null, PARLANTES);

    ArticuloSinMedidasException error =
        assertThrows(
            ArticuloSinMedidasException.class,
            () -> conReferencias.armar(List.of(linea(camiseta, 1), linea(parlante, 1))));

    assertEquals(parlante.id(), error.articulos().getFirst().varianteId());
  }
}
