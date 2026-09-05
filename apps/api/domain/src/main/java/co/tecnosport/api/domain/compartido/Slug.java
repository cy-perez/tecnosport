package co.tecnosport.api.domain.compartido;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Fragmento de URL en kebab-case estricto: minúsculas, dígitos y guiones simples. */
public record Slug(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");
  private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

  public Slug {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new SlugInvalidoException("Slug inválido: " + valor);
    }
  }

  /**
   * Deriva un slug candidato de un texto libre (p. ej. el nombre de un producto): minúsculas, sin
   * tildes ni diéresis, cualquier corrida de caracteres que no sea letra o dígito colapsa en un solo
   * guión. No garantiza unicidad contra el catálogo — eso lo resuelve quien llama, probando
   * sufijos si el candidato ya existe.
   */
  public static Slug generarDesde(String texto) {
    String sinDiacriticos =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD))
            .replaceAll("");
    String candidato =
        NO_ALFANUMERICO.matcher(sinDiacriticos.toLowerCase()).replaceAll("-").replaceAll("^-|-$", "");
    return new Slug(candidato);
  }
}
