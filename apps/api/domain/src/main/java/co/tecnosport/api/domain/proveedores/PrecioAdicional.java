package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;

/**
 * Un precio del anuncio que no es el del producto: el de un acompañante que no tiene foto propia
 * —«Gorra $35.000», «Básica 48.000»—, el de un combo —«Set falda + básica $88.000», «Dúo $95.000»—
 * o el de una promoción por cantidad —«Promo 4x200.000»—.
 *
 * <p>La Riverah los pone al pie de casi todos sus anuncios (10 de octubre de 2026). No son
 * productos: no tienen foto ni código, y el catálogo no tiene combos. Se guardan para que quien
 * revisa los vea, no para publicarlos.
 *
 * @param concepto lo que nombra el precio, como lo escribe el proveedor: «Gorra», «Set falda +
 *     básica», «Promo 4x»
 */
public record PrecioAdicional(String concepto, Dinero precio) {

  public PrecioAdicional {
    Objects.requireNonNull(concepto, "El precio adicional dice de qué es.");
    concepto = concepto.strip();
    if (concepto.isEmpty()) {
      throw new ExcepcionDeDominio("El precio adicional dice de qué es.");
    }
    Objects.requireNonNull(precio, "El precio adicional tiene precio.");
  }
}
