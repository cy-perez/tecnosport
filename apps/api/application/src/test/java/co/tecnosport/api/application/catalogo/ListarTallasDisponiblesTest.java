package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarTallasDisponiblesTest {

  private static TallasDisponibles listar(TallaEnExistencia... tallas) {
    return new ListarTallasDisponibles(() -> List.of(tallas)).ejecutar();
  }

  private static TallaEnExistencia ropa(String valor) {
    return new TallaEnExistencia(LineaCatalogo.ROPA, valor);
  }

  private static TallaEnExistencia calzado(String valor) {
    return new TallaEnExistencia(LineaCatalogo.CALZADO, valor);
  }

  /** Un jean 38 y un tenis 38 son dos opciones, una en cada desplegable. */
  @Test
  void separaLasTallasPorLinea() {
    TallasDisponibles tallas = listar(ropa("38"), calzado("38"), ropa("M"), calzado("40"));

    assertEquals(
        List.of(new OpcionDeTalla("38", "38"), new OpcionDeTalla("M", "M")), tallas.ropa());
    assertEquals(
        List.of(new OpcionDeTalla("38", "38"), new OpcionDeTalla("40", "40")), tallas.calzado());
  }

  /** «m» y «M», «2XL» y «XXL»: la misma casilla, y se lee como la escribe la escala. */
  @Test
  void juntaLasEscriturasDeUnaMismaTalla() {
    TallasDisponibles tallas = listar(ropa("m"), ropa("M"), ropa("2XL"), ropa("xxl"), ropa(" m "));

    assertEquals(
        List.of(new OpcionDeTalla("M", "M"), new OpcionDeTalla("XXL", "2XL")), tallas.ropa());
  }

  /** Decisión del negocio del 2 de noviembre de 2026: la talla única se ofrece como una más. */
  @Test
  void laTallaUnicaEntraConSuTilde() {
    TallasDisponibles tallas = listar(ropa("unica"), ropa("Única"), ropa("S"));

    assertEquals(
        List.of(new OpcionDeTalla("S", "S"), new OpcionDeTalla("UNICA", "Única")), tallas.ropa());
  }

  @Test
  void sinExistenciasNoHayTallas() {
    TallasDisponibles tallas = listar();

    assertEquals(List.of(), tallas.ropa());
    assertEquals(List.of(), tallas.calzado());
  }
}
