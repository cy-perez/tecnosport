package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El precio de contado de un equipo de tecnología le gana al costo del proveedor más de la ganancia
 * máxima ({@link MargenMinimo#deTecnologia}, ADR-0101). El panel traduce el código ({@code
 * PRECIO_SOBRE_GANANCIA_MAXIMA}); el mensaje sale como {@code detail} y dice el techo en pesos.
 */
public final class PrecioSobreGananciaMaximaException extends ExcepcionDeDominio {

  public PrecioSobreGananciaMaximaException(
      String que, Dinero precioContado, Dinero costo, Dinero gananciaMaxima) {
    super(
        "El precio de contado de "
            + que
            + " ("
            + precioContado.valor().toPlainString()
            + ") le gana a su costo ("
            + costo.valor().toPlainString()
            + ") más de la ganancia máxima, que es de "
            + gananciaMaxima.valor().toPlainString()
            + " pesos.");
  }
}
