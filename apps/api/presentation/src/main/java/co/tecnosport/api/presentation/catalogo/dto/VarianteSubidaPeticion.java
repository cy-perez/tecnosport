package co.tecnosport.api.presentation.catalogo.dto;

import java.util.Objects;

/** Un objeto ya subido y el ancho que le corresponde, al confirmar una imagen. */
public record VarianteSubidaPeticion(int ancho, String objectKey) {

  public VarianteSubidaPeticion {
    // Aquí no hay Bean Validation (apps/api/CLAUDE.md): lo que protege un DTO es su constructor
    // compacto. Sin esto, una key nula llega hasta el almacén y revienta lejos de donde se originó.
    Objects.requireNonNull(objectKey, "Cada variante necesita su objectKey.");
  }
}
