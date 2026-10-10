package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Las tallas que hay de cada tono, cuando el proveedor no las tiene todas en todos.
 *
 * <p>Violeta escribe «Talla SM ML(negro) / Talla ML(cocoa) / Talla SM(verde)» (10 de octubre de
 * 2026): del cocoa no hay SM. Con solo las tallas del producto, la aprobación hacía tono × talla y
 * publicaba un cocoa SM que el proveedor no tiene. Vacía cuando el mensaje no reparte las tallas
 * por tono, que es lo de casi siempre: entonces todos los tonos tienen todas.
 */
public record TallasPorTono(List<TallasDeUnTono> tonos) {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  /** Lo que la aprobación añade a un tono repetido entre prendas: «Negro 2». */
  private static final Pattern NUMERO_DE_PRENDA = Pattern.compile("\\s+\\d+$");

  public TallasPorTono {
    tonos = tonos == null ? List.of() : List.copyOf(tonos);
    List<String> vistos = new ArrayList<>();
    for (TallasDeUnTono tono : tonos) {
      String clave = clave(tono.tono());
      if (vistos.contains(clave)) {
        throw new ExcepcionDeDominio("El tono «" + tono.tono() + "» está dos veces.");
      }
      vistos.add(clave);
    }
  }

  public static TallasPorTono ninguna() {
    return new TallasPorTono(List.of());
  }

  public boolean estaVacia() {
    return tonos.isEmpty();
  }

  /**
   * Las tallas que hay de ese tono, si el mensaje las dijo. El tono se compara sin mayúsculas, sin
   * tildes y sin el número que la aprobación le pone a un tono repetido: las tallas de «cocoa» son
   * las de «Cocoa 2».
   */
  public Optional<List<String>> tallasDe(String tono) {
    if (tono == null) {
      return Optional.empty();
    }
    String clave = clave(NUMERO_DE_PRENDA.matcher(tono.strip()).replaceAll(""));
    return tonos.stream()
        .filter(t -> clave(t.tono()).equals(clave))
        .findFirst()
        .map(TallasDeUnTono::tallas);
  }

  /**
   * Las tallas en que se crea un tono al aprobar: las que el mensaje le dio, entre las que se
   * aprueban. El tono se busca por cada uno de sus nombres, en orden —el de la paleta con que se
   * aprueba y el que la lectura de fotos vio en sus fotos—, porque la paleta no tiene «cocoa» y
   * quien aprueba la marca «Café». Sin ninguno que coincida, o si quien aprueba quitó todas las de
   * ese tono, el tono va en todas: un tono sin variantes no tendría dónde colgar sus fotos.
   */
  public List<String> tallasPara(List<String> nombresDelTono, List<String> aprobadas) {
    for (String nombre : nombresDelTono) {
      Optional<List<String>> delTono = tallasDe(nombre);
      if (delTono.isPresent()) {
        List<String> comunes =
            aprobadas.stream()
                .filter(t -> delTono.get().stream().anyMatch(t::equalsIgnoreCase))
                .toList();
        return comunes.isEmpty() ? aprobadas : comunes;
      }
    }
    return aprobadas;
  }

  /**
   * Solo los tonos que el texto nombra: el reparto lo escribe el proveedor, y un tono que el
   * extractor puso sin que el mensaje lo diga quitaría tallas que sí hay.
   */
  public TallasPorTono contrastadoCon(String texto) {
    if (texto == null || tonos.isEmpty()) {
      return ninguna();
    }
    String plano = clave(texto);
    return new TallasPorTono(tonos.stream().filter(t -> plano.contains(clave(t.tono()))).toList());
  }

  private static String clave(String texto) {
    return DIACRITICOS
        .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT)
        .strip();
  }

  /**
   * @param tono como lo escribe el proveedor: «cocoa»
   * @param tallas las que hay de ese tono, en el orden del mensaje
   */
  public record TallasDeUnTono(String tono, List<String> tallas) {
    public TallasDeUnTono {
      Objects.requireNonNull(tono, "El tono no puede ser nulo.");
      tono = tono.strip();
      if (tono.isEmpty()) {
        throw new ExcepcionDeDominio("El tono no puede estar en blanco.");
      }
      tallas = tallas == null ? List.of() : List.copyOf(tallas);
      if (tallas.isEmpty()) {
        throw new ExcepcionDeDominio("Un tono con tallas trae al menos una.");
      }
    }
  }
}
