package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cómo talla un producto según el mensaje: talla única que «sirve hasta» una talla, una lista de
 * tallas, o no lo dijo. Los bolsos no tallan y quedan en {@code DESCONOCIDA} sin que sea una
 * alerta.
 */
public record Tallas(TipoDeTalla tipo, String sirveHasta, List<String> valores) {

  private static final Pattern SIRVE_HASTA = Pattern.compile("\\bsirven? hasta\\b");
  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

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

  /**
   * El «sirve hasta» solo vale si el mensaje lo dice con esas palabras (decidido por el negocio el
   * 3 de octubre de 2026): una talla límite deducida de «talla única» o de una foto es un dato
   * inventado, y en la ficha le promete al cliente algo que el proveedor no prometió.
   *
   * @param texto el mensaje del proveedor
   * @return estas mismas tallas, o la talla única sin límite si el texto no lo nombra
   */
  public Tallas sinSirveHastaQueElTextoNoDiga(String texto) {
    if (sirveHasta == null || dice(texto)) {
      return this;
    }
    return new Tallas(tipo, null, valores);
  }

  private static boolean dice(String texto) {
    if (texto == null) {
      return false;
    }
    String plano =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    return SIRVE_HASTA.matcher(plano).find();
  }

  public Optional<String> sirveHastaOpcional() {
    return Optional.ofNullable(sirveHasta);
  }
}
