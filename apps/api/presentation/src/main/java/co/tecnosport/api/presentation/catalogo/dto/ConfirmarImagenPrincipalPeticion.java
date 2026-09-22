package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.Objects;

/**
 * {@code variantes} es la lista de objetos ya subidos, uno por ancho; puede traer uno solo, que es
 * lo que manda el panel cuando una persona elige un archivo. {@code alto} y {@code hash} son los de
 * la variante mayor. {@code objectKeyVistaPrevia} es opcional.
 */
public record ConfirmarImagenPrincipalPeticion(
    List<VarianteSubidaPeticion> variantes,
    String objectKeyVistaPrevia,
    int alto,
    String hash,
    String altEs,
    String altEn) {

  public ConfirmarImagenPrincipalPeticion {
    Objects.requireNonNull(variantes, "Hay que confirmar al menos una variante de la imagen.");
  }
}
