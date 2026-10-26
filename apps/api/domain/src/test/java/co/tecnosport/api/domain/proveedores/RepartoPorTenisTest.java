package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Una tanda de Tenis Al Pie con el mismo tenis en varias fotos: un producto por tenis, no por foto
 * (ADR-0090). Los casos salen de la tanda del 16 de octubre de 2026: el Shox R4 en cinco fotos, el
 * Le Coq Sportif en tres y el McQueen dos veces con otro encuadre.
 */
class RepartoPorTenisTest {

  private static ProductoExtraido soloElPrecio() {
    return new ProductoExtraido(
        true,
        false,
        null,
        LineaCatalogo.CALZADO,
        TipoProductoProveedor.OTRO,
        Dinero.deCop(135000),
        null,
        null,
        List.of(),
        null,
        null,
        null,
        false,
        new BigDecimal("0.8"),
        null,
        null);
  }

  private static List<UUID> tanda(int cuantas) {
    return IntStream.range(0, cuantas).mapToObj(i -> UUID.randomUUID()).toList();
  }

  private static LecturaDeCalzado tenis(
      String marca, String modelo, boolean seguro, Integer mismoTenisQue) {
    return new LecturaDeCalzado(
        modelo, seguro, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, true, marca, mismoTenisQue);
  }

  private static LecturaDeCalzado puesto(Integer mismoTenisQue) {
    return new LecturaDeCalzado(
        "Shox R4", true, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, false, "Nike", mismoTenisQue);
  }

  private static LecturaDeFoto foto(int posicion, String color, LecturaDeCalzado calzado) {
    return foto(posicion, color, List.of(), calzado);
  }

  private static LecturaDeFoto foto(
      int posicion, String color, List<String> marcasLeidas, LecturaDeCalzado calzado) {
    return new LecturaDeFoto(
        posicion,
        List.of(),
        null,
        List.of(),
        List.of(color),
        "tenis",
        marcasLeidas,
        List.of(),
        calzado);
  }

  private static List<ProductoRepartido> repartir(List<UUID> fotos, LecturaDeFoto... leidas) {
    return RepartoDeFotos.unaPorFoto(
        soloElPrecio(), fotos, new LecturaDeFotos(false, List.of(leidas), "{}"), true);
  }

  /** El Shox R4 en negro, plata y gris: un producto con las tres fotos y un tono por foto. */
  @Test
  void elMismoTenisEnVariosColoresEsUnProducto() {
    List<UUID> fotos = tanda(4);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "negro", tenis("Nike", "Shox R4", true, 0)),
            foto(1, "plata", tenis("Nike", "Shox R4", true, 0)),
            foto(2, "blanco", tenis("Le Coq Sportif", null, false, 2)),
            foto(3, "gris", tenis("Nike", "Shox R4", false, 0)));

    assertEquals(2, repartidos.size());
    ProductoRepartido shox = repartidos.getFirst();
    assertEquals(List.of(fotos.get(0), fotos.get(1), fotos.get(3)), shox.fotos());
    assertEquals(Optional.of(fotos.get(0)), shox.exclusivaOpcional());
    assertEquals(
        Map.of(fotos.get(0), "negro", fotos.get(1), "plata", fotos.get(3), "gris"),
        shox.reparto().tonosSugeridos());
    assertEquals(
        Optional.of("Tenis importados estilo Nike Shox R4"), shox.producto().tituloOpcional());
    assertTrue(shox.disenoDeAlbum(), "comparte el precio del texto con la tanda");
    assertEquals(1, shox.reparto().ajenas().size());
    assertEquals(List.of(fotos.get(2)), repartidos.get(1).fotos());
  }

  /** Lo que el lector diga de una foto lleva a la primera, aunque pase por otra. */
  @Test
  void lasCadenasLleganALaPrimeraFoto() {
    List<UUID> fotos = tanda(3);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "negro", tenis("Nike", "Shox R4", true, 0)),
            foto(1, "plata", tenis("Nike", "Shox R4", true, 0)),
            foto(2, "gris", tenis("Nike", "Shox R4", true, 1)));

    assertEquals(1, repartidos.size());
    assertEquals(fotos, repartidos.getFirst().fotos());
  }

  /** Una que apunta hacia adelante y una vuelta entre dos fotos no parten el tenis. */
  @Test
  void haciaAdelanteOEnVueltaSigueSiendoElMismoTenis() {
    List<UUID> fotos = tanda(4);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "negro", tenis("Nike", "Shox R4", true, 1)),
            foto(1, "plata", tenis("Nike", "Shox R4", true, 1)),
            foto(2, "negro", tenis("Dior", "B22", true, 3)),
            foto(3, "blanco", tenis("Dior", "B22", true, 2)));

    assertEquals(2, repartidos.size());
    assertEquals(List.of(fotos.get(0), fotos.get(1)), repartidos.get(0).fotos());
    assertEquals(List.of(fotos.get(2), fotos.get(3)), repartidos.get(1).fotos());
  }

  /** Un número fuera de la tanda no dice de qué tenis es: la foto queda sola. */
  @Test
  void unNumeroFueraDeLaTandaDejaLaFotoSola() {
    List<UUID> fotos = tanda(2);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "negro", tenis("Nike", "Shox R4", true, 0)),
            foto(1, "plata", tenis("Nike", "Shox R4", true, 9)));

    assertEquals(2, repartidos.size());
  }

  /**
   * El tenis puesto no se publica, pero las fotos solas que apuntan a él son del mismo tenis: el
   * grupo no se parte porque su primera foto no se venda.
   */
  @Test
  void laFotoPuestaNoEntraNiParteElTenis() {
    List<UUID> fotos = tanda(3);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "negro", puesto(0)),
            foto(1, "negro", tenis("Nike", "Shox R4", true, 0)),
            foto(2, "plata", tenis("Nike", "Shox R4", true, 0)));

    assertEquals(1, repartidos.size());
    assertEquals(List.of(fotos.get(1), fotos.get(2)), repartidos.getFirst().fotos());
    assertEquals(Optional.of(fotos.get(1)), repartidos.getFirst().exclusivaOpcional());
  }

  /**
   * El gallo de Le Coq Sportif no es un texto que se lea: la marca por parecido es la del título.
   * Sin ella, las que se leen, como antes.
   */
  @Test
  void laMarcaPorParecidoGanaALaLeida() {
    List<UUID> fotos = tanda(2);

    List<ProductoRepartido> repartidos =
        repartir(
            fotos,
            foto(0, "azul", List.of("LCS"), tenis("Le Coq Sportif", "R1000", true, 0)),
            foto(1, "negro", List.of("Vans"), tenis(null, null, false, 1)));

    assertEquals(
        Optional.of("Tenis importados estilo Le Coq Sportif R1000"),
        repartidos.get(0).producto().tituloOpcional());
    assertEquals(List.of("Le Coq Sportif"), repartidos.get(0).producto().marcas());
    assertEquals(
        Optional.of("Tenis importados estilo Vans"), repartidos.get(1).producto().tituloOpcional());
  }

  /**
   * Si una foto ve el rasgo que distingue el modelo y otra no, manda la segura, aunque la dudosa se
   * repita: el Shox R4 de lado tiene las cuatro columnas a la vista, de frente no.
   */
  @Test
  void elModeloSeguroGanaAlDudoso() {
    List<UUID> fotos = tanda(3);

    ProductoRepartido shox =
        repartir(
                fotos,
                foto(0, "negro", tenis("Nike", "Shox TL", false, 0)),
                foto(1, "plata", tenis("Nike", "Shox TL", false, 0)),
                foto(2, "gris", tenis("Nike", "Shox R4", true, 0)))
            .getFirst();

    assertEquals(
        Optional.of("Tenis importados estilo Nike Shox R4"), shox.producto().tituloOpcional());
    assertFalse(shox.calzadoOpcional().orElseThrow().modeloPorConfirmar());
  }

  /** Sin ninguna segura, el más repetido, y queda por confirmar. */
  @Test
  void sinModeloSeguroVaElMasRepetidoPorConfirmar() {
    List<UUID> fotos = tanda(3);

    ProductoRepartido jordan =
        repartir(
                fotos,
                foto(0, "blanco", tenis("Jordan", "Jordan 4", false, 0)),
                foto(1, "negro", tenis("Jordan", "Jordan 3", false, 0)),
                foto(2, "gris", tenis("Jordan", "Jordan 3", false, 0)))
            .getFirst();

    assertEquals(
        Optional.of("Tenis importados estilo Jordan 3"), jordan.producto().tituloOpcional());
    assertTrue(jordan.calzadoOpcional().orElseThrow().modeloPorConfirmar());
    assertEquals("jordan jordan 3", jordan.reparto().diseno());
  }

  /**
   * Sin modelo no hay etiqueta de diseño: la del lector era tan general que el panel juntaba como
   * «posible mismo diseño» un McQueen, un Dior y un Nike.
   */
  @Test
  void sinModeloNoHayEtiquetaDeDiseno() {
    ProductoRepartido sinModelo =
        repartir(tanda(1), foto(0, "negro", tenis("Alexander McQueen", null, false, 0))).getFirst();

    assertNull(sinModelo.reparto().diseno());
  }

  /** Una lectura guardada antes del 26 de octubre de 2026 no dice nada del grupo: una por foto. */
  @Test
  void unaLecturaSinGrupoDejaCadaFotoSola() {
    List<UUID> fotos = tanda(3);
    List<LecturaDeFoto> leidas = new ArrayList<>();
    for (int i = 0; i < fotos.size(); i++) {
      leidas.add(
          foto(
              i,
              "negro",
              new LecturaDeCalzado(
                  "Shox R4", true, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, true)));
    }

    List<ProductoRepartido> repartidos = repartir(fotos, leidas.toArray(new LecturaDeFoto[0]));

    assertEquals(3, repartidos.size());
  }

  /** Fuera del calzado, la foto es el producto, como siempre. */
  @Test
  void fueraDelCalzadoCadaFotoEsUnProducto() {
    List<UUID> fotos = tanda(2);

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.unaPorFoto(
            soloElPrecio(),
            fotos,
            new LecturaDeFotos(
                false,
                List.of(
                    foto(0, "negro", tenis("Nike", "Shox R4", true, 0)),
                    foto(1, "plata", tenis("Nike", "Shox R4", true, 0))),
                "{}"),
            false);

    assertEquals(2, repartidos.size());
  }
}
