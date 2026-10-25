package co.tecnosport.api.domain.proveedores;

import java.util.List;
import java.util.Objects;

/**
 * Si el color de una variante de tecnología hay que confirmarlo con el proveedor antes de venderla
 * (ADR-0086).
 *
 * <p>La lista del proveedor casi nunca dice colores, y la paleta oficial del modelo dice cuáles
 * existen, no cuáles tiene él hoy. Solo dos cosas lo dejan sabido sin preguntar: que la lista de
 * hoy lo marque con su emoji, o que el modelo exista en un único color. Todo lo demás se pregunta
 * por WhatsApp, y se compra con el enlace que se genera cuando el proveedor dice que sí.
 */
public final class ConfirmacionDeColor {

  private ConfirmacionDeColor() {}

  /**
   * @param paleta los colores oficiales del modelo; vacía si no se conocen
   * @param deLaLista los colores que la lista de hoy marca de su configuración; vacía si no marca
   *     ninguno
   * @param color el de la variante
   */
  public static boolean requiere(List<String> paleta, List<String> deLaLista, String color) {
    Objects.requireNonNull(color, "El color de la variante no puede ser nulo.");
    List<String> oficiales = Objects.requireNonNullElse(paleta, List.of());
    if (oficiales.size() == 1 && ConfiguracionTecnologia.mismoColor(oficiales.get(0), color)) {
      return false;
    }
    return Objects.requireNonNullElse(deLaLista, List.<String>of()).stream()
        .noneMatch(c -> ConfiguracionTecnologia.mismoColor(c, color));
  }

  /**
   * Los colores que la lista marcó de verdad, deducidos de los sugeridos, para cuando no llegan
   * aparte: un borrador, o una lista que exportó una versión de la skill anterior a ADR-0086.
   *
   * <p>Desde el 22 de octubre de 2026 la skill sugiere la paleta completa cuando la lista no marca
   * colores, así que unos sugeridos que cubren toda la paleta no dicen nada del proveedor. Se lee
   * como «no marcó ninguno»: si de verdad los marcó todos, lo peor que pasa es que se pregunta lo
   * que ya se sabía, y la lista siguiente, que sí los manda aparte, lo corrige.
   */
  public static List<String> marcadosSegunLosSugeridos(
      List<String> paleta, List<String> sugeridos) {
    List<String> oficiales = Objects.requireNonNullElse(paleta, List.of());
    List<String> propuestos = Objects.requireNonNullElse(sugeridos, List.of());
    boolean cubreLaPaleta =
        !oficiales.isEmpty()
            && oficiales.stream()
                .allMatch(
                    o ->
                        propuestos.stream()
                            .anyMatch(p -> ConfiguracionTecnologia.mismoColor(o, p)));
    return cubreLaPaleta ? List.of() : List.copyOf(propuestos);
  }
}
