package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.Respaldo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lo que dejó la revisión de la ingesta del 11 de octubre de 2026: «AA» es réplica, un título con
 * «estilo» y marca se revisa como réplica, el polo se titula como polo, y un anuncio de varias
 * marcas sale en un borrador por marca. Con los anuncios reales de La Riverah.
 */
class ReplicasPolosYMarcasTest {

  private static final UUID F1 = UUID.randomUUID();
  private static final UUID F2 = UUID.randomUUID();
  private static final UUID F3 = UUID.randomUUID();
  private static final UUID F4 = UUID.randomUUID();

  private static ProductoExtraido extraido(
      String titulo, TipoProductoProveedor tipo, boolean replica, List<String> marcas) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        tipo,
        Dinero.deCop(55000),
        Tallas.lista(List.of("M", "L", "XL")),
        null,
        List.of(),
        null,
        "Una prenda.",
        null,
        replica,
        new BigDecimal("0.9"),
        null,
        null,
        null,
        null,
        marcas);
  }

  // --- «AA» y «estilo» ---

  @Test
  void aaEnMayusculasEsReplicaYNoLaDeUnaPila() {
    assertTrue(PatronDeReplica.esReplica("*Jeans AA plus para caballero* 👖👖👖"));
    assertTrue(PatronDeReplica.esReplica("*Jeans AA para caballero* ⭕️Marca Levis"));
    assertFalse(PatronDeReplica.esReplica("Control remoto, usa pilas AA"));
    assertFalse(PatronDeReplica.esReplica("Talla aa, color negro"), "en minúsculas no la anuncia");
    assertFalse(PatronDeReplica.esReplica("Referencia AAB12"));
    assertTrue(PatronDeReplica.esReplica("Superstar Importado AAA"), "AAA sigue igual");
  }

  @Test
  void elAaNoQuedaEnElTitulo() {
    assertEquals(
        "Jean plus para caballero", PatronDeReplica.sinMarca("Jean AA plus para caballero"));
    assertEquals("Control con pilas AA", PatronDeReplica.sinMarca("Control con pilas AA"));
  }

  @Test
  void unTituloConEstiloYMarcaSeRevisaComoReplica() {
    assertTrue(PatronDeReplica.nombraMarcaConEstilo("Pantalón estilo Levis para caballero"));
    assertTrue(PatronDeReplica.nombraMarcaConEstilo("Tenis estilo For One"));
    assertFalse(PatronDeReplica.nombraMarcaConEstilo("Pantalón jogger para dama importado"));
    assertFalse(PatronDeReplica.nombraMarcaConEstilo("Pantalón estilo importado"));
    assertFalse(PatronDeReplica.nombraMarcaConEstilo(null));

    ProductoExtraido levis =
        extraido(
                "Pantalón estilo Levis para caballero", TipoProductoProveedor.PANTALON, false, null)
            .contrastadoCon("*Jeans para caballero* ✅Tela Licrada 🤑🤑*55.000*");
    assertTrue(levis.esReplica(), "el extractor la tituló como réplica: alguien la revisa");
  }

  // --- El polo ---

  @Test
  void elTituloDeUnPoloEsPoloYElResto() {
    assertEquals("Polo importado", TituloDePolo.componer("Camiseta estilo Polo importada", true));
    assertEquals(
        "Polo importado estilo Puma - BMW",
        TituloDePolo.componer("Camiseta estilo Puma - BMW", true));
    assertEquals("Polo importado estilo Puma", TituloDePolo.componer("Polo estilo Puma", true));
    assertEquals(
        "Polo para caballero", TituloDePolo.componer("Camisa tipo polo para caballero", false));
    assertEquals(
        "Polo importado manga corta", TituloDePolo.componer("Camiseta polo manga corta", true));
    assertEquals(
        "Polo importado estilo Lacoste",
        TituloDePolo.componer("Polo importado estilo Lacoste", true),
        "lo que ya tiene la forma se queda igual");
  }

  @Test
  void unAnuncioDeCamisasTipoPoloEsDePolos() {
    assertTrue(TituloDePolo.anunciaPolo("CAMISETA POLO 1.1 ⭕ tipo polo ⭕Calidad 1.1"));
    assertTrue(TituloDePolo.anunciaPolo("*NUEVA POLO 1.1🍯* *MARCA P R A D A*"));
    assertTrue(TituloDePolo.anunciaPolo("Camisa cuello polo"));
    assertFalse(TituloDePolo.anunciaPolo("Camiseta estampada Polo Ralph Lauren"));
    assertFalse(TituloDePolo.anunciaPolo("Camisetas oversize para caballero"));
  }

  /** Las 23 camisas tipo polo de La Riverah, que salieron como «Camiseta estilo Polo importada». */
  @Test
  void lasCamisasTipoPoloDeLaRiverahSalenComoPolos() {
    String texto =
        "*NUEVA COLECCIÓN* 🏃🏃 CAMISETA POLO 1.1 👦👦 ⭕ tipo polo ⭕Calidad 1.1 ⭕M L Xl 2XL"
            + " 🤑 $55.000🥳";

    ProductoExtraido polo =
        extraido("Camiseta estilo Polo importada", TipoProductoProveedor.CAMISETA, true, null)
            .contrastadoCon(texto);

    assertEquals(TipoProductoProveedor.POLO, polo.tipo());
    assertEquals("Polo importado", polo.titulo());
    assertTrue(polo.esReplica());
  }

  @Test
  void unaCamisetaQueNoEsPoloSigueSiendoCamiseta() {
    ProductoExtraido camiseta =
        extraido("Camiseta oversize para caballero", TipoProductoProveedor.CAMISETA, false, null)
            .contrastadoCon("Camisetas Oversize para Caballero 👕 Cuello redondo $55.000");

    assertEquals(TipoProductoProveedor.CAMISETA, camiseta.tipo());
    assertEquals("Camiseta oversize para caballero", camiseta.titulo());
  }

  // --- Varias marcas ---

  @Test
  void conUnaMarcaElTituloNombraSoloEsa() {
    ProductoExtraido varias =
        extraido(
            "Pantalón estilo Diesel - Amiri - Dsq purple - DG importado",
            TipoProductoProveedor.PANTALON,
            true,
            List.of("Diesel", "Amiri", "Dsquared2", "Dolce & Gabbana"));

    ProductoExtraido amiri = varias.conMarca("Amiri");

    assertEquals("Pantalón estilo Amiri importado", amiri.titulo());
    assertEquals(List.of("Amiri"), amiri.marcas());
    assertTrue(amiri.esReplica());
    assertEquals(
        "Jean para caballero estilo Diesel",
        extraido("Jean para caballero", TipoProductoProveedor.PANTALON, true, null)
            .conMarca("Diesel")
            .titulo());
  }

  private static LecturaDeFoto conMarcas(int posicion, List<String> marcas) {
    return new LecturaDeFoto(posicion, List.of(), null, List.of(), List.of("azul"), null, marcas);
  }

  /** Cada referencia de marca, con sus fotos, en su propio borrador (decidido el 11/10/2026). */
  @Test
  void unAnuncioDeVariasMarcasSaleEnUnBorradorPorMarca() {
    ProductoExtraido jeans =
        extraido(
            "Pantalón estilo Diesel - Amiri importado",
            TipoProductoProveedor.PANTALON,
            true,
            List.of("Diesel", "Amiri"));
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            false,
            List.of(
                conMarcas(0, List.of("DIESEL")),
                conMarcas(1, List.of("Amiri")),
                conMarcas(2, List.of()),
                conMarcas(3, List.of("Diesel"))),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(jeans), List.of(F1, F2, F3, F4), lectura);

    assertEquals(2, repartidos.size());
    ProductoRepartido diesel = repartidos.get(0);
    assertEquals("Pantalón estilo Diesel importado", diesel.producto().titulo());
    assertEquals(List.of(F1, F3, F4), diesel.fotos(), "las suyas y la que no dice de quién es");
    assertEquals(Set.of(F2), diesel.reparto().ajenas());
    assertEquals(F1, diesel.exclusiva());
    assertEquals(Respaldo.MARCA_VISIBLE, diesel.respaldo());
    assertTrue(diesel.compartidas());
    assertTrue(diesel.disenoDeAlbum(), "comparten el precio del anuncio");
    ProductoRepartido amiri = repartidos.get(1);
    assertEquals("Pantalón estilo Amiri importado", amiri.producto().titulo());
    assertEquals(List.of(F2, F3), amiri.fotos());
  }

  /** Puma y BMW en la misma prenda son una colaboración: un solo borrador. */
  @Test
  void unaColaboracionNoSeParte() {
    ProductoExtraido polo =
        extraido(
            "Polo importado estilo Puma - BMW",
            TipoProductoProveedor.POLO,
            true,
            List.of("Puma", "BMW"));
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            false,
            List.of(conMarcas(0, List.of("Puma", "BMW")), conMarcas(1, List.of("PUMA", "BMW"))),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(polo), List.of(F1, F2), lectura);

    assertEquals(1, repartidos.size());
    assertEquals("Polo importado estilo Puma - BMW", repartidos.getFirst().producto().titulo());
  }

  /** La marca se lee aunque el lector la escriba distinto: «A|X», «D&G», con tildes. */
  @Test
  void laMarcaSeReconoceSinSignosNiMayusculas() {
    LecturaDeFoto foto = conMarcas(0, List.of("A|X", "Dolce&Gabbana"));

    assertTrue(foto.muestraMarca("AX"));
    assertTrue(foto.muestraMarca("Dolce & Gabbana"));
    assertFalse(foto.muestraMarca("Diesel"));
    assertFalse(foto.muestraMarca(" "));
  }

  /**
   * En un álbum, cada SKU ya es un producto: el que tiene la foto con el logo de Amiri se titula
   * con Amiri, no con las cuatro marcas del anuncio.
   */
  @Test
  void enUnAlbumCadaDisenoLlevaLaMarcaQueSeLeeEnSusFotos() {
    ProductoExtraido jeans =
        extraido(
            "Pantalón estilo Diesel - Amiri importado",
            TipoProductoProveedor.PANTALON,
            true,
            List.of("Diesel", "Amiri"));
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                new LecturaDeFoto(
                    0, List.of(), "RV1", List.of("32"), List.of("azul"), "a", List.of("Amiri")),
                new LecturaDeFoto(
                    1, List.of(), "RV2", List.of("34"), List.of("negro"), "b", List.of())),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(jeans), List.of(F1, F2), lectura);

    assertEquals(2, repartidos.size());
    assertEquals("Pantalón estilo Amiri importado", repartidos.get(0).producto().titulo());
    assertEquals(
        "Pantalón estilo Diesel - Amiri importado",
        repartidos.get(1).producto().titulo(),
        "sin marca legible no hay con qué decidir");
  }
}
