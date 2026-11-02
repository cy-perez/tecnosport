package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Las tallas que ofrecen los dos desplegables de talla de la vitrina: solo las que tienen algo que
 * comprar detrás, por el mismo criterio que las marcas del filtro — ofrecer una talla agotada es
 * mandar a quien compra a una rejilla vacía.
 *
 * <p>Las variantes escriben la misma talla de varias maneras —«m» y «M», «2XL» y «XXL»—, así que
 * aquí se juntan por su forma normalizada, que es la que el filtro de la búsqueda compara. El orden
 * es el alfabético de esa forma; el de la escala —XS antes que S— lo pone la vitrina, que ya tiene
 * la escala de cada categoría.
 *
 * <p>«Única» entra con las demás: es una talla que se vende, y quien la busca es quien no quiere
 * pelear con la tabla de medidas.
 */
public final class ListarTallasDisponibles {

  private final ConsultaDeTallasEnExistencia consulta;

  public ListarTallasDisponibles(ConsultaDeTallasEnExistencia consulta) {
    this.consulta = Objects.requireNonNull(consulta, "La consulta de tallas no puede ser nula.");
  }

  public TallasDisponibles ejecutar() {
    List<TallaEnExistencia> tallas = consulta.tallasEnExistencia();
    return new TallasDisponibles(
        opcionesDe(tallas, LineaCatalogo.ROPA), opcionesDe(tallas, LineaCatalogo.CALZADO));
  }

  private static List<OpcionDeTalla> opcionesDe(
      List<TallaEnExistencia> tallas, LineaCatalogo linea) {
    Map<String, List<String>> porNormalizada =
        tallas.stream()
            .filter(talla -> talla.linea() == linea)
            .map(TallaEnExistencia::valor)
            .filter(valor -> valor != null && !valor.isBlank())
            .map(String::strip)
            .distinct()
            .collect(
                Collectors.groupingBy(
                    Atributo::tallaNormalizada, TreeMap::new, Collectors.toList()));
    return porNormalizada.entrySet().stream()
        .map(
            grupo ->
                new OpcionDeTalla(grupo.getKey(), etiquetaDe(grupo.getKey(), grupo.getValue())))
        .toList();
  }

  /**
   * La escritura que ya es la normalizada, si alguna variante la usa —«M» antes que «m»—; si no, la
   * primera que empieza en mayúscula —«Única» antes que «unica», que es la que conserva la tilde
   * que la normalizada quita—; y si ninguna, la primera en orden.
   */
  private static String etiquetaDe(String normalizada, List<String> escrituras) {
    List<String> enOrden = escrituras.stream().sorted().toList();
    return enOrden.stream()
        .filter(normalizada::equals)
        .findFirst()
        .or(() -> enOrden.stream().filter(e -> Character.isUpperCase(e.charAt(0))).findFirst())
        .orElse(enOrden.get(0));
  }
}
