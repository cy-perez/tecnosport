package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El precio de contado no le deja al negocio el {@link MargenMinimo} sobre el costo del proveedor.
 * El panel traduce el código ({@code PRECIO_SIN_MARGEN_MINIMO}); el mensaje sale como {@code
 * detail} para el registro y para quien llame a la API sin panel, y dice la ganancia que se exigía
 * en pesos: desde el 27 de octubre de 2026 depende de la línea y del costo, y ya no es un
 * porcentaje fijo.
 */
public final class PrecioSinMargenMinimoException extends ExcepcionDeDominio {

  public PrecioSinMargenMinimoException(
      String que, Dinero precioContado, Dinero costo, Dinero gananciaMinima) {
    super(
        "El precio de contado de "
            + que
            + " ("
            + precioContado.valor().toPlainString()
            + ") le gana a su costo ("
            + costo.valor().toPlainString()
            + ") menos de la ganancia mínima, que es de "
            + gananciaMinima.valor().toPlainString()
            + " pesos.");
  }
}
