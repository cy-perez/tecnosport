package co.tecnosport.api.domain.compartido;

import java.util.regex.Pattern;

/** Fragmento de URL en kebab-case estricto: minúsculas, dígitos y guiones simples. */
public record Slug(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  public Slug {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new SlugInvalidoException("Slug inválido: " + valor);
    }
  }
}
