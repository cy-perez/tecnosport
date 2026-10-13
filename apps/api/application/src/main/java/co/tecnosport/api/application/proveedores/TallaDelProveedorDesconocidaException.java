package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.AjusteDeTalla;

/**
 * El proveedor nombra las tallas corridas —horma reducida, talla EUR— y una de las que se aprueban
 * no se puede contar: la XXXL una más arriba, la talla única (ADR-0085). Sin saber cuál pedirle,
 * esa talla no se vende.
 */
public final class TallaDelProveedorDesconocidaException extends RuntimeException {

  public TallaDelProveedorDesconocidaException(String talla, AjusteDeTalla ajuste) {
    super(
        "El proveedor nombra cada talla "
            + Math.abs(ajuste.tallas())
            + (ajuste.tallas() > 0 ? " más arriba" : " más abajo")
            + ", y la "
            + talla
            + " no tiene con qué nombre pedírsela. Quítala de las tallas antes de aprobar.");
  }
}
