package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;

/**
 * Lo que un borrador guarda de haber sido armado como set: con qué prenda y de dónde salió su
 * costo. Es lo que el panel le explica a quien revisa, lo que distingue su huella de la de la
 * prenda sola y lo que suma la gorra al peso del envío.
 *
 * @param acompanante la blusa o la gorra que va con la prenda
 * @param origenDelCosto si el proveedor anunció el precio del set o se sumaron las prendas
 */
public record ArmadoDeSet(PrendaAcompanante acompanante, OrigenDelCostoDeSet origenDelCosto) {

  public ArmadoDeSet {
    Objects.requireNonNull(acompanante, "Un set dice con qué prenda va.");
    Objects.requireNonNull(origenDelCosto, "Un set dice de dónde salió su costo.");
    if (acompanante == PrendaAcompanante.OTRA) {
      throw new ExcepcionDeDominio("Solo la blusa y la gorra arman un set.");
    }
  }

  /** Los gramos que el set pesa de más sobre el promedio de su categoría. */
  public int gramosAdicionales() {
    return acompanante.gramosAdicionales();
  }
}
