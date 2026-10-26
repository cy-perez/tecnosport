package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lo que el lector vio de un tenis en todas sus fotos de la tanda, en una sola lectura (ADR-0090).
 * Cada foto del grupo es otra mirada al mismo tenis, y el lector no siempre dice lo mismo de todas:
 * reconoce el Shox R4 de lado y no de frente. Manda lo que más se repite, y en un empate, lo de la
 * primera foto; el modelo seguro gana al dudoso aunque se repita menos, porque el seguro vio el
 * rasgo que los distingue.
 */
public final class LecturaDelTenis {

  private LecturaDelTenis() {}

  /**
   * @param leidas las lecturas de las fotos del tenis, en el orden de la tanda; todas de un tenis
   *     que se vende
   */
  public static LecturaDeCalzado de(List<LecturaDeFoto> leidas) {
    List<LecturaDeCalzado> calzados =
        leidas.stream().map(LecturaDeFoto::calzadoOpcional).flatMap(Optional::stream).toList();
    if (calzados.isEmpty()) {
      throw new IllegalArgumentException("Un tenis de la tanda tiene al menos una lectura.");
    }
    Optional<String> seguro =
        masRepetido(
            calzados.stream().filter(LecturaDeCalzado::modeloSeguro).toList(), c -> c.modelo());
    String modelo = seguro.or(() -> masRepetido(calzados, LecturaDeCalzado::modelo)).orElse(null);
    GeneroDeCalzado genero =
        masRepetido(calzados, c -> c.genero() == null ? null : c.genero().name())
            .map(GeneroDeCalzado::valueOf)
            .orElse(null);
    return new LecturaDeCalzado(
        modelo,
        seguro.isPresent(),
        genero,
        TipoDeCalzado.TENIS,
        true,
        masRepetido(calzados, LecturaDeCalzado::marca).orElse(null),
        calzados.getFirst().mismoTenisQue());
  }

  /**
   * Lo que el lector vio del tenis en las fotos de un producto de otro proveedor (ADR-0090): solo
   * las que muestran un tenis, puesto o no —Imperio Wicho fotografía el tenis en el pie, y eso no
   * impide reconocerlo—. Vacía si ninguna lo muestra.
   */
  public static Optional<LecturaDeCalzado> deLasQueMuestranUnTenis(List<LecturaDeFoto> leidas) {
    List<LecturaDeFoto> conTenis =
        leidas.stream()
            .filter(
                l -> l.calzadoOpcional().map(c -> c.tipo() == TipoDeCalzado.TENIS).orElse(false))
            .toList();
    return conTenis.isEmpty() ? Optional.empty() : Optional.of(de(conTenis));
  }

  /** Las marcas que se leen escritas en alguna de sus fotos, sin repetir y en orden. */
  public static List<String> marcasLeidas(List<LecturaDeFoto> leidas) {
    Map<String, String> marcas = new LinkedHashMap<>();
    leidas.stream()
        .flatMap(l -> l.marcas().stream())
        .filter(m -> m != null && !m.isBlank())
        .forEach(m -> marcas.putIfAbsent(m.strip().toLowerCase(Locale.ROOT), m.strip()));
    return new ArrayList<>(marcas.values());
  }

  /**
   * El valor que más se repite, sin distinguir mayúsculas, escrito como la primera vez que
   * apareció; vacío si ninguno tiene valor.
   */
  private static Optional<String> masRepetido(
      List<LecturaDeCalzado> calzados, Function<LecturaDeCalzado, String> valor) {
    Map<String, String> escritura = new LinkedHashMap<>();
    Map<String, Integer> veces = new LinkedHashMap<>();
    for (LecturaDeCalzado calzado : calzados) {
      String uno = valor.apply(calzado);
      if (uno == null || uno.isBlank()) {
        continue;
      }
      String clave = uno.strip().toLowerCase(Locale.ROOT);
      escritura.putIfAbsent(clave, uno.strip());
      veces.merge(clave, 1, Integer::sum);
    }
    String mejor = null;
    for (Map.Entry<String, Integer> entrada : veces.entrySet()) {
      if (mejor == null || entrada.getValue() > veces.get(mejor)) {
        mejor = entrada.getKey();
      }
    }
    return Optional.ofNullable(mejor).map(escritura::get);
  }
}
