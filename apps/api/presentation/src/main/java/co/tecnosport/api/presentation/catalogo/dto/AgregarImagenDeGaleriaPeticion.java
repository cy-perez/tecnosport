package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.Objects;

/** Sin {@code orden}: lo decide el agregado, que es quien sabe cuál es el siguiente. */
public record AgregarImagenDeGaleriaPeticion(
    List<VarianteSubidaPeticion> variantes,
    String objectKeyVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn) {

  public AgregarImagenDeGaleriaPeticion {
    Objects.requireNonNull(variantes, "Hay que confirmar al menos una variante de la imagen.");
  }
}
