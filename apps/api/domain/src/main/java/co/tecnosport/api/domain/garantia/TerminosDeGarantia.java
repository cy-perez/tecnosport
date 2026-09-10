package co.tecnosport.api.domain.garantia;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Cuántos meses dura la garantía legal según la categoría del producto.
 *
 * <p><b>No es un número global</b>, y ese es el punto. El catálogo mezcla ropa, calzado y
 * celulares; los términos publicados prometen un año desde la entrega "salvo que el productor
 * anuncie uno mayor" y, para celulares, el del fabricante. Un único valor incrustado para todo el
 * catálogo daría la respuesta equivocada en una de las tres líneas del negocio.
 *
 * <p>{@code sinTerminoConocido} es lo que impide inventarlo. Una categoría listada ahí no cae al
 * término por defecto: responde {@link Optional#empty()}, y quien pregunte recibe {@code
 * INDETERMINADA} en vez de un plazo que nadie decidió. Hoy vive ahí la categoría de celulares,
 * esperando el dato del fabricante.
 */
public final class TerminosDeGarantia {

  private final int mesesPorDefecto;
  private final Map<String, Integer> mesesPorCategoria;
  private final Set<String> sinTerminoConocido;

  private TerminosDeGarantia(
      int mesesPorDefecto, Map<String, Integer> mesesPorCategoria, Set<String> sinTerminoConocido) {
    this.mesesPorDefecto = mesesPorDefecto;
    this.mesesPorCategoria = mesesPorCategoria;
    this.sinTerminoConocido = sinTerminoConocido;
  }

  public static TerminosDeGarantia de(
      int mesesPorDefecto, Map<String, Integer> mesesPorCategoria, Set<String> sinTerminoConocido) {
    if (mesesPorDefecto <= 0) {
      throw new ExcepcionDeDominio("El término de garantía por defecto tiene que ser positivo.");
    }
    Objects.requireNonNull(mesesPorCategoria, "El mapa por categoría no puede ser nulo.");
    Objects.requireNonNull(sinTerminoConocido, "El conjunto de pendientes no puede ser nulo.");
    Map<String, Integer> copia = new HashMap<>();
    mesesPorCategoria.forEach(
        (categoria, meses) -> {
          if (meses == null || meses <= 0) {
            throw new ExcepcionDeDominio(
                "El término de garantía de " + categoria + " tiene que ser positivo.");
          }
          copia.put(categoria, meses);
        });
    Set<String> pendientes = Set.copyOf(sinTerminoConocido);
    for (String categoria : pendientes) {
      if (copia.containsKey(categoria)) {
        throw new ExcepcionDeDominio(
            "La categoría "
                + categoria
                + " no puede tener término configurado y estar declarada como pendiente a la vez.");
      }
    }
    return new TerminosDeGarantia(mesesPorDefecto, Map.copyOf(copia), pendientes);
  }

  /** Vacío significa "nadie ha decidido este término todavía", no "no tiene garantía". */
  public Optional<Integer> mesesPara(String categoria) {
    if (categoria != null && sinTerminoConocido.contains(categoria)) {
      return Optional.empty();
    }
    if (categoria != null && mesesPorCategoria.containsKey(categoria)) {
      return Optional.of(mesesPorCategoria.get(categoria));
    }
    return Optional.of(mesesPorDefecto);
  }
}
