package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Lo menos y lo más que el precio sugerido le gana a cada unidad, en pesos, sea cual sea el factor:
 * 20.000 y 30.000 desde el 3 de octubre de 2026, para todas las líneas y también cuando el
 * proveedor trae su propio factor. Ajustan la sugerencia; quien aprueba puede poner otro precio.
 */
public record TopesDeGanancia(Dinero minima, Dinero maxima) {

  public TopesDeGanancia {
    if (minima == null || maxima == null) {
      throw new ExcepcionDeDominio("Los topes de ganancia no pueden ser nulos.");
    }
    if (minima.valor().signum() < 0) {
      throw new ExcepcionDeDominio("La ganancia mínima no puede ser negativa.");
    }
    if (minima.valor().compareTo(maxima.valor()) > 0) {
      throw new ExcepcionDeDominio("La ganancia mínima no puede superar la máxima.");
    }
  }
}
