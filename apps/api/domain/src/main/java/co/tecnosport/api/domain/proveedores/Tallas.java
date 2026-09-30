package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Optional;

/**
 * Cómo talla un producto según el mensaje: talla única que «sirve hasta» una talla, una lista de
 * tallas, o no lo dijo. Los bolsos no tallan y quedan en {@code DESCONOCIDA} sin que sea una
 * alerta.
 */
public record Tallas(TipoDeTalla tipo, String sirveHasta, List<String> valores) {

  public Tallas {
    if (tipo == null) {
      tipo = TipoDeTalla.DESCONOCIDA;
    }
    valores = valores == null ? List.of() : List.copyOf(valores);
    sirveHasta = sirveHasta == null || sirveHasta.isBlank() ? null : sirveHasta.strip();
    if (tipo == TipoDeTalla.LISTA && valores.isEmpty()) {
      throw new ExcepcionDeDominio("Una lista de tallas tiene que traer al menos una.");
    }
    if (tipo != TipoDeTalla.LISTA && !valores.isEmpty()) {
      throw new ExcepcionDeDominio("Solo una lista de tallas lleva valores.");
    }
  }

  public static Tallas desconocida() {
    return new Tallas(TipoDeTalla.DESCONOCIDA, null, List.of());
  }

  public static Tallas unica(String sirveHasta) {
    return new Tallas(TipoDeTalla.UNICA, sirveHasta, List.of());
  }

  public static Tallas lista(List<String> valores) {
    return new Tallas(TipoDeTalla.LISTA, null, valores);
  }

  public Optional<String> sirveHastaOpcional() {
    return Optional.ofNullable(sirveHasta);
  }
}
