package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * El <b>precio de contado</b> que se sugiere para una configuración de tecnología (ADR-0095,
 * decisión 8): el de mercado, pero sin ganarle al costo más que la ganancia máxima —400.000 desde
 * el 26 de octubre de 2026—.
 *
 * <pre>
 * contado sugerido = min(precioMercado, costo + gananciaMaxima)
 * </pre>
 *
 * <p>El precio publicado sugerido sale de este con la comisión de la pasarela, como cualquier otro
 * precio de contado. No hay ganancia mínima: el piso lo pone la skill de listas, que ya excluye lo
 * que queda por debajo del costo o con menos de un 5 % de margen. Sin precio de mercado no hay
 * sugerencia, y quien revisa fija el contado. Como en la ropa, la sugerencia no obliga.
 *
 * <h2>El redondeo</h2>
 *
 * <p>Cuando manda el mercado, la sugerencia es el precio de mercado <b>tal cual</b>, aunque no sea
 * redondo: es el precio contra el que se compite y redondearlo lo movería. Cuando manda el tope, el
 * costo más la ganancia máxima tampoco suele ser redondo, y se baja a la unidad de mil anterior: el
 * precio de vitrina va en miles, y subir rompería el tope. Por eso la ganancia máxima es de por lo
 * menos mil pesos: con menos, bajar a la unidad de mil podría dejar la sugerencia por debajo del
 * costo.
 *
 * @param gananciaMaxima lo más que el contado sugerido le gana al costo
 */
public record SugerenciaDePrecioTecnologia(Dinero gananciaMaxima) {

  private static final BigDecimal MIL = BigDecimal.valueOf(1000);

  public SugerenciaDePrecioTecnologia {
    Objects.requireNonNull(gananciaMaxima, "La ganancia máxima de tecnología no puede ser nula.");
    if (gananciaMaxima.valor().compareTo(MIL) < 0) {
      throw new ExcepcionDeDominio(
          "La ganancia máxima de tecnología es de por lo menos mil pesos: "
              + gananciaMaxima.valor().toPlainString());
    }
  }

  /** El contado sugerido de esa configuración; nulo si la lista no trajo su precio de mercado. */
  public Dinero contadoSugerido(ConfiguracionTecnologia configuracion) {
    return contadoSugerido(
        configuracion.costoProveedor(), configuracion.precioMercado(), gananciaMaxima);
  }

  /**
   * {@code min(precioMercado, costo + gananciaMaxima)}, con el tope bajado a la unidad de mil.
   *
   * @return nulo si no hay precio de mercado
   */
  public static Dinero contadoSugerido(Dinero costo, Dinero precioMercado, Dinero gananciaMaxima) {
    Objects.requireNonNull(costo, "La sugerencia necesita el costo del proveedor.");
    Objects.requireNonNull(gananciaMaxima, "La sugerencia necesita la ganancia máxima.");
    if (precioMercado == null) {
      return null;
    }
    BigDecimal tope = costo.valor().add(gananciaMaxima.valor());
    if (precioMercado.valor().compareTo(tope) <= 0) {
      return precioMercado;
    }
    return Dinero.deCop(tope.divide(MIL, 0, RoundingMode.FLOOR).multiply(MIL));
  }
}
