package co.tecnosport.api.domain.proveedores;

import java.util.Locale;
import java.util.Optional;

/**
 * Para quién es un tenis, según lo que se ve en la foto (ADR-0089). Decide la rama del calzado
 * donde se publica —Dama, Caballero o Unisex— y qué tallas se le ofrecen.
 */
public enum GeneroDeCalzado {
  DAMA,
  CABALLERO,
  UNISEX;

  /**
   * El de la rama del calzado donde cuelga una categoría: «calzado-caballero» y sus hojas, como
   * «calzado-caballero-deportivo», son de caballero. Una hija lleva siempre el slug de su padre
   * delante ({@code CrearCategoria}), así que basta el prefijo.
   *
   * @return vacío si la categoría no es de calzado
   */
  public static Optional<GeneroDeCalzado> deCategoria(String slug) {
    if (slug == null) {
      return Optional.empty();
    }
    for (GeneroDeCalzado genero : values()) {
      String rama = "calzado-" + genero.name().toLowerCase(Locale.ROOT);
      if (slug.equals(rama) || slug.startsWith(rama + "-")) {
        return Optional.of(genero);
      }
    }
    return Optional.empty();
  }
}
