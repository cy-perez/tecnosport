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

  private static Direccion conDireccion(String direccion) {
    return new Direccion("05", "Antioquia", "05001", "Medellín", direccion, null, "Boston");
  }

  /**
   * Una dirección colombiana necesita dígitos y almohadilla, así que "solo letras" habría prohibido
   * la dirección entera. Lo que se prohíbe es lo que no puede formar parte de una.
   */
  @Test
  void acepta_lo_que_de_verdad_escribe_un_comprador() {
    assertEquals("Cra 43A #7-50 Apto 902", conDireccion("Cra 43A #7-50 Apto 902").direccion());
    assertEquals(
        "Calle 10 Sur / Vereda El Salado",
        conDireccion("Calle 10 Sur / Vereda El Salado").direccion());
    assertEquals(
        "Km 3 vía Las Palmas, casa 4°", conDireccion("Km 3 vía Las Palmas, casa 4°").direccion());
  }

  @Test
  void rechaza_una_direccion_con_simbolos_que_no_se_pueden_imprimir() {
    assertThrows(ExcepcionDeDominio.class, () -> conDireccion("@#$%"));
    assertThrows(ExcepcionDeDominio.class, () -> conDireccion("<script>alert(1)</script>"));
  }

  /** "43-25" no dice a dónde ir: hace falta al menos una letra. */
  @Test
  void rechaza_una_direccion_sin_una_sola_letra() {
    assertThrows(ExcepcionDeDominio.class, () -> conDireccion("43-25"));
  }

  /**
   * El barrio sigue sin exigirse —vacío pasa, y eso lo cubren las pruebas de arriba—, pero lo que
   * se escriba tiene que poder imprimirse. Los dígitos sí: "20 de Julio" es un barrio.
   */
  @Test
  void el_barrio_opcional_tambien_tiene_forma() {
    assertEquals(Optional.of("20 de Julio"), conBarrio("20 de Julio").barrioDeclarado());
    assertEquals(Optional.of("Belén (Rincón)"), conBarrio("Belén (Rincón)").barrioDeclarado());
    assertThrows(ExcepcionDeDominio.class, () -> conBarrio("@#$%"));
    assertThrows(ExcepcionDeDominio.class, () -> conBarrio("<b>Laureles</b>"));
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
