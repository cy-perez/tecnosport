package co.tecnosport.api.domain.compartido;

/** Identificador de variante, único en el catálogo. Normalizado a mayúsculas. */
public record Sku(String valor) {

  public Sku {
    if (valor == null || valor.isBlank()) {
      throw new SkuInvalidoException("El SKU no puede estar vacío.");
    }
    valor = valor.trim().toUpperCase();
  }
}
