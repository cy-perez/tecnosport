package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El precio de venta que se sugiere a partir del precio del proveedor y un factor: se multiplica y
 * se sube a la <b>unidad de mil siguiente</b> —72.500 es 73.000, 78.900 es 79.000 y 79.000 se queda
 * en 79.000—. Es una sugerencia: quien aprueba pone el precio final.
 *
 * <p>Hasta el 12 de octubre de 2026 redondeaba a la centena con {@code HALF_UP}, y el panel sugería
 * precios como 71.600 que nadie publicaba así: quien aprobaba los subía a mano, uno por uno. El
 * precio de vitrina del negocio va en miles redondos, y la sugerencia ahora también.
 *
 * <h2>Los topes de ganancia</h2>
 *
 * <p>Después del factor, lo que la venta le gana al proveedor se lleva a los {@link
 * TopesDeGanancia}: a un bolso de 40.000 el 1,35 le gana 14.000 y se sugiere a 60.000; a uno de
 * 200.000 le gana 70.000 y se sugiere a 230.000. Cuando el tope manda, el redondeo a la unidad de
 * mil va hacia dentro —hacia arriba en el mínimo, hacia <b>abajo</b> en el máximo— para que el tope
 * se cumpla aunque el precio del proveedor no sea redondo: en el máximo, subir rompería el tope, y
 * bajar a la unidad de mil anterior conserva el precio redondo.
 */
public final class CalculadoraDeMargen {

  private static final BigDecimal MIL = BigDecimal.valueOf(1000);

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
    BigDecimal conFactor = aMil(costo.multiply(factor), RoundingMode.CEILING);
    BigDecimal ganancia = conFactor.subtract(costo);
    if (ganancia.compareTo(topes.minima().valor()) < 0) {
      BigDecimal conMinima = aMil(costo.add(topes.minima().valor()), RoundingMode.CEILING);
      // Con topes a menos de mil pesos el uno del otro, subir a la unidad de mil puede pasarse del
      // máximo; ahí manda el máximo, hacia abajo.
      if (conMinima.subtract(costo).compareTo(topes.maxima().valor()) > 0) {
        return Dinero.deCop(aMil(costo.add(topes.maxima().valor()), RoundingMode.FLOOR));
      }
      return Dinero.deCop(conMinima);
    }
    // Se compara la ganancia ya redondeada: subir a la unidad de mil también puede ser lo que la
    // pasa del máximo.
    if (ganancia.compareTo(topes.maxima().valor()) > 0) {
      return Dinero.deCop(aMil(costo.add(topes.maxima().valor()), RoundingMode.FLOOR));
    }
    return Dinero.deCop(conFactor);
  }

  private static BigDecimal aMil(BigDecimal valor, RoundingMode modo) {
    return valor.divide(MIL, 0, modo).multiply(MIL);
  }
}
