package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El precio de venta que se sugiere a partir del precio del proveedor y un factor: se multiplica y
 * se redondea a la centena de pesos, una sola vez y {@code HALF_UP}, como todo el dinero del
 * proyecto. Es una sugerencia: quien aprueba pone el precio final.
 *
 * <h2>Los topes de ganancia</h2>
 *
 * <p>Después del factor, lo que la venta le gana al proveedor se lleva a los {@link
 * TopesDeGanancia}: a un bolso de 40.000 el 1,35 le gana 14.000 y se sugiere a 60.000; a uno de
 * 200.000 le gana 70.000 y se sugiere a 230.000. Cuando el tope manda, el redondeo a la centena va
 * hacia dentro —hacia arriba en el mínimo, hacia abajo en el máximo— para que el tope se cumpla
 * aunque el precio del proveedor no sea redondo.
 */
public final class CalculadoraDeMargen {

  private static final BigDecimal CENTENA = BigDecimal.valueOf(100);

  private CalculadoraDeMargen() {}

  public static Dinero sugerir(Dinero precioProveedor, BigDecimal factor, TopesDeGanancia topes) {
    if (precioProveedor == null || factor == null || topes == null) {
      throw new ExcepcionDeDominio(
          "El margen necesita el precio del proveedor, el factor y los topes de ganancia.");
    }
    if (factor.compareTo(BigDecimal.ONE) < 0) {
      throw new ExcepcionDeDominio("El factor de margen no puede ser menor que 1.");
    }
    BigDecimal costo = precioProveedor.valor();
    BigDecimal conFactor = aCentena(costo.multiply(factor), RoundingMode.HALF_UP);
    BigDecimal ganancia = conFactor.subtract(costo);
    if (ganancia.compareTo(topes.minima().valor()) < 0) {
      return Dinero.deCop(aCentena(costo.add(topes.minima().valor()), RoundingMode.CEILING));
    }
    if (ganancia.compareTo(topes.maxima().valor()) > 0) {
      return Dinero.deCop(aCentena(costo.add(topes.maxima().valor()), RoundingMode.FLOOR));
    }
    return Dinero.deCop(conFactor);
  }

  private static BigDecimal aCentena(BigDecimal valor, RoundingMode modo) {
    return valor.divide(CENTENA, 0, modo).multiply(CENTENA);
  }
}
