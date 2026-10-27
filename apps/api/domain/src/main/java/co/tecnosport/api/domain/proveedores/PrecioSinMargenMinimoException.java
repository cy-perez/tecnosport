package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El precio de contado no le deja al negocio el {@link MargenMinimo} sobre el costo del proveedor.
 * El panel traduce el código ({@code PRECIO_SIN_MARGEN_MINIMO}); el mensaje queda para el registro
 * y para quien llame a la API sin panel.
 */
public final class PrecioSinMargenMinimoException extends ExcepcionDeDominio {

  public PrecioSinMargenMinimoException(String que, Dinero precioContado, Dinero costo) {
    super(
        "El precio de contado de "
            + que
            + " ("
            + precioContado.valor().toPlainString()
            + ") deja menos del 10 % de margen sobre su costo ("
            + costo.valor().toPlainString()
            + ").");
  }
}
