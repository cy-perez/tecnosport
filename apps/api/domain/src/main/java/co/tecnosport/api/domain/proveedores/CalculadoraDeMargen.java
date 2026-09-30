package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El precio de venta que se sugiere a partir del precio del proveedor y un factor: se multiplica y
 * se redondea a la centena de pesos, una sola vez y {@code HALF_UP}, como todo el dinero del
 * proyecto. Es una sugerencia: quien aprueba pone el precio final.
 */
public final class CalculadoraDeMargen {

  private static final BigDecimal CENTENA = BigDecimal.valueOf(100);

  private CalculadoraDeMargen() {}

  public static Dinero sugerir(Dinero precioProveedor, BigDecimal factor) {
    if (precioProveedor == null || factor == null) {
      throw new ExcepcionDeDominio("El margen necesita el precio del proveedor y el factor.");
    }
    if (factor.compareTo(BigDecimal.ONE) < 0) {
      throw new ExcepcionDeDominio("El factor de margen no puede ser menor que 1.");
    }
    BigDecimal exacto = precioProveedor.valor().multiply(factor);
    BigDecimal centenas = exacto.divide(CENTENA, 0, RoundingMode.HALF_UP);
    return Dinero.deCop(centenas.multiply(CENTENA));
  }
}
