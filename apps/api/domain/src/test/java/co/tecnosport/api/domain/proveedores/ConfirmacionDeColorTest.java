package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfirmacionDeColorTest {

  private static final List<String> PALETA = List.of("Negro", "Morado", "Plata");

  @Test
  void un_color_de_la_paleta_que_la_lista_no_marca_se_confirma() {
    assertTrue(ConfirmacionDeColor.requiere(PALETA, List.of(), "Morado"));
    assertTrue(ConfirmacionDeColor.requiere(PALETA, List.of("Negro"), "Morado"));
  }

  @Test
  void el_que_la_lista_marca_con_su_emoji_no_se_confirma() {
    assertFalse(ConfirmacionDeColor.requiere(PALETA, List.of("Negro", "morado"), " Morado "));
  }

  @Test
  void un_modelo_de_un_solo_color_no_tiene_nada_que_confirmar() {
    assertFalse(ConfirmacionDeColor.requiere(List.of("Blanco"), List.of(), "blanco"));
  }

  @Test
  void sin_paleta_se_confirma_salvo_lo_que_marque_la_lista() {
    assertTrue(ConfirmacionDeColor.requiere(List.of(), List.of(), "Negro"));
    assertFalse(ConfirmacionDeColor.requiere(List.of(), List.of("Negro"), "Negro"));
  }

  @Test
  void la_paleta_de_un_color_no_libra_a_otro_color_que_no_es_el_suyo() {
    assertTrue(ConfirmacionDeColor.requiere(List.of("Blanco"), List.of(), "Negro"));
  }

  @Test
  void unos_sugeridos_que_cubren_la_paleta_no_dicen_que_marco_la_lista() {
    assertEquals(
        List.of(),
        ConfirmacionDeColor.marcadosSegunLosSugeridos(PALETA, List.of("Plata", "Negro", "morado")));
  }

  @Test
  void unos_sugeridos_parciales_son_los_que_marco_la_lista() {
    assertEquals(
        List.of("Negro"), ConfirmacionDeColor.marcadosSegunLosSugeridos(PALETA, List.of("Negro")));
  }

  @Test
  void sin_paleta_los_sugeridos_son_lo_unico_que_hay() {
    assertEquals(
        List.of("Negro"),
        ConfirmacionDeColor.marcadosSegunLosSugeridos(List.of(), List.of("Negro")));
  }
}
