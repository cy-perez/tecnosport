package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Objects;

/**
 * Lo que la skill de listas ya decidió de un modelo de tecnología: el título sin memoria ni SIM, la
 * descripción de la ficha y la paleta oficial de la marca.
 *
 * <p>La marca y la categoría llegan como texto —«Samsung», «celulares»— y no como ids del catálogo:
 * la skill no conoce la base, y quien aprueba elige las de verdad en el panel con estas a la vista.
 *
 * @param paleta los colores oficiales del modelo, en el orden de la marca; vacía si no se conocen,
 *     y entonces quien revisa escribe los colores a mano
 */
public record ModeloDeLista(
    String idModelo,
    String titulo,
    String marca,
    String categoria,
    String descripcion,
    String metaDescripcion,
    List<String> paleta) {

  public ModeloDeLista {
    if (idModelo == null || idModelo.isBlank()) {
      throw new ExcepcionDeDominio("Un modelo de la lista tiene su id.");
    }
    if (titulo == null || titulo.isBlank()) {
      throw new ExcepcionDeDominio("Un modelo de la lista tiene título.");
    }
    if (descripcion == null || descripcion.isBlank()) {
      throw new ExcepcionDeDominio(
          "Un modelo sin descripción no se importa: la skill todavía no terminó su ficha.");
    }
    idModelo = idModelo.strip();
    titulo = titulo.strip();
    marca = enBlancoEsNulo(marca);
    categoria = enBlancoEsNulo(categoria);
    descripcion = descripcion.strip();
    metaDescripcion = enBlancoEsNulo(metaDescripcion);
    paleta =
        paleta == null
            ? List.of()
            : paleta.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(c -> !c.isEmpty())
                .distinct()
                .toList();
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }
}
