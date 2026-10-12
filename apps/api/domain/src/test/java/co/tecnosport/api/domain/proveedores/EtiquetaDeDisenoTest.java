package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lo que solo cambia en el color no es otro diseño: los tenis de Imperio Wicho del 11 de octubre de
 * 2026, un modelo en tres combinaciones, salieron en tres borradores.
 */
class EtiquetaDeDisenoTest {

  @Test
  void quitaLosColoresYLoQueLosUne() {
    assertEquals("tenis franja", EtiquetaDeDiseno.sinColores("tenis franja azul y roja"));
    assertEquals("tenis franja", EtiquetaDeDiseno.sinColores("Tenis franja blanca"));
    assertEquals("tenis franja", EtiquetaDeDiseno.sinColores("tenis negro franja negra"));
    assertEquals("running malla", EtiquetaDeDiseno.sinColores("running malla gris claro"));
    assertEquals("running malla", EtiquetaDeDiseno.sinColores("running malla azul oscuro"));
    assertEquals("bolso ejecutivo", EtiquetaDeDiseno.sinColores("bolso café ejecutivo"));
    assertEquals("blusa", EtiquetaDeDiseno.sinColores("blusa rosa palo"));
    assertEquals("jean", EtiquetaDeDiseno.sinColores("jean azules / negros"));
  }

  @Test
  void loQueNoEsColorSeQueda() {
    assertEquals("jordan 23 arco", EtiquetaDeDiseno.sinColores("jordan 23 arco"));
    assertEquals("msm logo grande", EtiquetaDeDiseno.sinColores("msm logo grande"));
    assertEquals("logo en pecho", EtiquetaDeDiseno.sinColores("logo en pecho"));
    assertEquals("rotos pintura", EtiquetaDeDiseno.sinColores("rotos pintura"));
  }

  @Test
  void soloColoresNoEsUnDiseno() {
    assertNull(EtiquetaDeDiseno.sinColores("negro"));
    assertNull(EtiquetaDeDiseno.sinColores("azul oscuro y blanco"));
    assertNull(EtiquetaDeDiseno.sinColores(" "));
    assertNull(EtiquetaDeDiseno.sinColores(null));
  }

  /**
   * El lector dijo álbum y les dio tres etiquetas que solo difieren en el color: es un producto en
   * tres tonos, no tres productos.
   */
  @Test
  void unModeloEnTresCombinacionesEsUnSoloProducto() {
    UUID f1 = UUID.randomUUID();
    UUID f2 = UUID.randomUUID();
    UUID f3 = UUID.randomUUID();
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                new LecturaDeFoto(
                    0, List.of(), null, List.of(), List.of("negro"), "tenis franja negra"),
                new LecturaDeFoto(
                    1,
                    List.of(),
                    null,
                    List.of(),
                    List.of("negro", "blanco"),
                    "tenis franja blanca"),
                new LecturaDeFoto(
                    2,
                    List.of(),
                    null,
                    List.of(),
                    List.of("blanco", "azul", "rojo"),
                    "tenis franja azul y roja")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(tenis()), List.of(f1, f2, f3), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(f1, f2, f3), repartidos.getFirst().fotos());
    assertFalse(repartidos.getFirst().disenoDeAlbum());
  }

  private static ProductoExtraido tenis() {
    return new ProductoExtraido(
        true,
        false,
        "Tenis para caballero importado",
        LineaCatalogo.ROPA,
        TipoProductoProveedor.TENIS,
        Dinero.deCop(65000),
        Tallas.lista(List.of("40", "41", "42")),
        null,
        List.of(),
        null,
        "Unos tenis.",
        null,
        false,
        new BigDecimal("0.9"),
        null,
        null,
        null,
        null,
        List.of());
  }
}
