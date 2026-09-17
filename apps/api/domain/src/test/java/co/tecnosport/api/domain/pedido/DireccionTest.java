package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * El barrio es el único campo opcional de la dirección junto con las indicaciones, y esa asimetría
 * es deliberada: en el origen la plataforma de envíos lo exige para programar la recolección, y en
 * el destino solo mejora la entrega.
 */
class DireccionTest {

  private static Direccion conBarrio(String barrio) {
    return new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null, barrio);
  }

  @Test
  void el_barrio_se_guarda_cuando_lo_escribieron() {
    assertEquals(Optional.of("Boston"), conBarrio("Boston").barrioDeclarado());
  }

  /**
   * En blanco es lo mismo que no haberlo escrito, y se normaliza aquí y no en cada mapeador: de
   * este valor depende que la clave {@code area_level3} viaje o no, y una cadena vacía mandada como
   * barrio es justo lo que la plataforma rechaza.
   */
  @Test
  void un_barrio_en_blanco_es_lo_mismo_que_no_tenerlo() {
    assertTrue(conBarrio("   ").barrioDeclarado().isEmpty());
    assertTrue(conBarrio("").barrioDeclarado().isEmpty());
    assertTrue(conBarrio(null).barrioDeclarado().isEmpty());
  }

  @Test
  void los_espacios_de_los_extremos_no_llegan_a_la_guia() {
    assertEquals(Optional.of("Boston"), conBarrio("  Boston  ").barrioDeclarado());
  }

  /** La fábrica con nombre dice en el sitio de la llamada que ahí de verdad no hay barrio. */
  @Test
  void sin_barrio_construye_una_direccion_valida_y_vacia_de_barrio() {
    Direccion direccion =
        Direccion.sinBarrio(
            "05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

    assertTrue(direccion.barrioDeclarado().isEmpty());
    assertEquals("Casa azul", direccion.indicaciones());
  }

  /** Lo que sí sigue siendo obligatorio: sin esto no se puede cotizar ni imprimir una guía. */
  @Test
  void lo_que_identifica_el_destino_sigue_siendo_obligatorio() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new Direccion(null, "Antioquia", "05001", "Medellín", "Cra. 26C", null, "Boston"));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new Direccion("05", "Antioquia", "05001", "Medellín", "  ", null, "Boston"));
  }
}
