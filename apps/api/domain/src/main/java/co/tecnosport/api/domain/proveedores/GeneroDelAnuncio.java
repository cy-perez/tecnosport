package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Para quién es el tenis según el texto del anuncio (ADR-0092): «IMPORTADO CABALLERO👟», «NIKE
 * UNISEX ULTRALIVIANO». Imperio Wicho lo escribe y Tenis Al Pie no, y lo que escribe quien tiene el
 * tenis en la mano manda sobre lo que se adivina en la foto. Nombrar a la vez dama y caballero
 * —«Horma normal Dama, Aumenta 1 Cab»— es unisex. Las dos se abrevian: «Importado AAA Dam y Cab»
 * (27 de octubre de 2026).
 */
public final class GeneroDelAnuncio {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
  private static final Pattern UNISEX = Pattern.compile("(?<!\\p{L})unisex(?!\\p{L})");
  private static final Pattern DAMA =
      Pattern.compile("(?<!\\p{L})(?:damas?|dam|mujer(?:es)?)(?!\\p{L})");
  private static final Pattern CABALLERO =
      Pattern.compile("(?<!\\p{L})(?:caballeros?|hombres?|cab)(?!\\p{L})");

  private GeneroDelAnuncio() {}

  /** Vacío si el texto no dice para quién es. */
  public static Optional<GeneroDeCalzado> de(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    String plano =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT);
    boolean dama = DAMA.matcher(plano).find();
    boolean caballero = CABALLERO.matcher(plano).find();
    if (UNISEX.matcher(plano).find() || (dama && caballero)) {
      return Optional.of(GeneroDeCalzado.UNISEX);
    }
    if (dama) {
      return Optional.of(GeneroDeCalzado.DAMA);
    }
    return caballero ? Optional.of(GeneroDeCalzado.CABALLERO) : Optional.empty();
  }
}
