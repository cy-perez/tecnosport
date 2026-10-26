package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Una tanda de Tenis Al Pie: cada foto un producto, y solo los tenis en una foto que se puede
 * publicar (ADR-0089 y ADR-0091).
 */
class RepartoUnaPorFotoTest {

  private final UUID airForce = UUID.randomUUID();
  private final UUID captura = UUID.randomUUID();
  private final UUID crocs = UUID.randomUUID();
  private final UUID samba = UUID.randomUUID();
  private final UUID cajaVacia = UUID.randomUUID();
  private final List<UUID> tanda = List.of(airForce, captura, crocs, samba, cajaVacia);

  /** El texto de la tanda: solo el precio. */
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

  private static LecturaDeFoto foto(
      int posicion, List<String> marcas, String color, LecturaDeCalzado calzado) {
    return new LecturaDeFoto(
        posicion, List.of(), null, List.of(), List.of(color), null, marcas, List.of(), calzado);
  }

  private LecturaDeFotos lectura() {
    return new LecturaDeFotos(
        false,
        List.of(
            foto(
                0,
                List.of("Nike"),
                "blanco",
                new LecturaDeCalzado(
                    "Air Force 1", true, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, true)),
            foto(
                1,
                List.of("Nike"),
                "negro",
                new LecturaDeCalzado(
                    "Jordan 4", false, GeneroDeCalzado.CABALLERO, TipoDeCalzado.TENIS, false)),
            foto(
                2,
                List.of(),
                "azul",
                new LecturaDeCalzado(null, false, null, TipoDeCalzado.ZUECO, true)),
            foto(
                3,
                List.of("Adidas"),
                "blanco",
                new LecturaDeCalzado(
                    "Samba", false, GeneroDeCalzado.DAMA, TipoDeCalzado.TENIS, true)),
            foto(4, List.of(), "negro", null)),
        "{}");
  }

  @Test
  void soloLosTenisEnUnaFotoPublicableLleganABorrador() {
    List<ProductoRepartido> repartidos =
        RepartoDeFotos.unaPorFoto(soloElPrecio(), tanda, lectura(), true);

    assertEquals(2, repartidos.size(), "ni la captura, ni la Crocs, ni la caja vacía");
    assertEquals(List.of(airForce), repartidos.get(0).fotos());
    assertEquals(List.of(samba), repartidos.get(1).fotos());
  }

  /** Cada uno con su foto como exclusiva, las demás ajenas, y el precio del texto compartido. */
  @Test
  void cadaFotoEsSuExclusivaYLasDemasLeSonAjenas() {
    ProductoRepartido airForce1 =
        RepartoDeFotos.unaPorFoto(soloElPrecio(), tanda, lectura(), true).getFirst();

    assertEquals(Optional.of(airForce), airForce1.exclusivaOpcional());
    assertTrue(airForce1.disenoDeAlbum(), "comparte el precio del texto con la tanda");
    assertEquals(4, airForce1.reparto().ajenas().size());
    assertEquals("blanco", airForce1.reparto().tonosSugeridos().get(airForce));
    assertEquals("nike air force 1", airForce1.reparto().diseno());
  }

  /**
   * El título sale de la foto, con el modelo aunque el lector dude (ADR-0090): el dudoso queda por
   * confirmar.
   */
  @Test
  void elTituloLlevaLaMarcaYElModeloAunqueDude() {
    List<ProductoRepartido> repartidos =
        RepartoDeFotos.unaPorFoto(soloElPrecio(), tanda, lectura(), true);

    ProductoExtraido airForce1 = repartidos.get(0).producto();
    assertEquals(
        Optional.of("Tenis importados estilo Nike Air Force 1"), airForce1.tituloOpcional());
    assertEquals(TipoProductoProveedor.TENIS, airForce1.tipo());
    assertTrue(airForce1.esReplica());
    assertEquals(
        Optional.of("Tenis importados estilo Adidas Samba"),
        repartidos.get(1).producto().tituloOpcional());
    assertTrue(repartidos.get(1).calzadoOpcional().orElseThrow().modeloPorConfirmar());
    assertEquals(
        Optional.of(GeneroDeCalzado.DAMA),
        repartidos.get(1).calzadoOpcional().flatMap(LecturaDeCalzado::generoOpcional));
  }

  /** Sin lectura no se sabe qué hay en cada foto: cada una sale con el producto del texto. */
  @Test
  void sinLecturaCadaFotoSaleConElProductoDelTexto() {
    List<ProductoRepartido> repartidos =
        RepartoDeFotos.unaPorFoto(soloElPrecio(), tanda, null, true);

    assertEquals(tanda.size(), repartidos.size());
    assertTrue(repartidos.stream().allMatch(r -> r.calzadoOpcional().isEmpty()));
  }
}
