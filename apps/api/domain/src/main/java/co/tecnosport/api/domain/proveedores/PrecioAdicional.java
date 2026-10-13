package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.Optional;

/**
 * Un precio del anuncio que no es el del producto: el de un acompañante que no tiene foto propia
 * —«Gorra $35.000», «Básica 48.000»—, el de un combo —«Set falda + básica $88.000», «Dúo $95.000»—
 * o el de una promoción por cantidad —«Promo 4x200.000»—.
 *
 * <p>La Riverah los pone al pie de casi todos sus anuncios (10 de octubre de 2026). Hasta el 12 de
 * octubre solo se guardaban para que quien revisa los viera. Desde entonces, a un proveedor que
 * vende sets el acompañante y el combo le arman el borrador ({@link ArmadorDeSets}), y para eso el
 * extractor dice la clase y la prenda que acompaña.
 *
 * @param concepto lo que nombra el precio, como lo escribe el proveedor: «Gorra», «Set falda +
 *     básica», «Promo 4x»
 * @param clase qué es; nula en los guardados antes del 12 de octubre de 2026
 * @param prenda la que acompaña a la del anuncio en un acompañante o un combo; nula en una
 *     promoción y en los guardados antes
 */
public record PrecioAdicional(
    String concepto, Dinero precio, ClaseDePrecioAdicional clase, PrendaAcompanante prenda) {

  public PrecioAdicional {
    Objects.requireNonNull(concepto, "El precio adicional dice de qué es.");
    concepto = concepto.strip();
    if (concepto.isEmpty()) {
      throw new ExcepcionDeDominio("El precio adicional dice de qué es.");
    }
    Objects.requireNonNull(precio, "El precio adicional tiene precio.");
  }

  /** Sin clase ni prenda: la forma de antes del 12 de octubre de 2026. */
  public PrecioAdicional(String concepto, Dinero precio) {
    this(concepto, precio, null, null);
  }

  public Optional<ClaseDePrecioAdicional> claseOpcional() {
    return Optional.ofNullable(clase);
  }

  public Optional<PrendaAcompanante> prendaOpcional() {
    return Optional.ofNullable(prenda);
  }
}
