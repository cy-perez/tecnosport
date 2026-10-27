package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Lo que cobra la pasarela por cada venta y cómo se mete dentro del precio publicado (ADR-0095).
 *
 * <p>El negocio fija el <b>precio de contado</b>, el que quiere recibir neto. El <b>precio
 * publicado</b> es el que se anuncia: el de contado más la comisión de Wompi, de modo que quien
 * paga por la pasarela le deja al negocio por lo menos el de contado:
 *
 * <pre>
 * exacto    = (contado + fija × (1 + iva)) / (1 − variable × (1 + iva))
 * publicado = centenaSiguiente(exacto)      si exacto &lt; 100.000
 *             unidadDeMilSiguiente(exacto)  si no
 * </pre>
 *
 * <p>El IVA multiplica las dos partes porque Wompi lo cobra sobre su comisión, no sobre la venta.
 * Se sube —nunca se baja—: bajar dejaría al negocio unos pesos por debajo del contado justo en la
 * venta por la pasarela, que es la que este cálculo protege.
 *
 * <p>Por debajo de 100.000 se sube a la centena y no a la unidad de mil (decisión del negocio del
 * 27 de octubre de 2026): con el cargo fijo dentro, subir a la unidad de mil publicaba un producto
 * de 8.000 a 10.000, un 25 % más, cuando la comisión solo explica 9.200. En lo barato el redondeo
 * pesaba más que la comisión; desde 100.000 la vitrina vuelve a los miles redondos.
 *
 * <p>Las tres tarifas son configuración ({@code tecnosport.wompi.comision.*}), no constantes: si
 * Wompi cambia el plan, cambian sin desplegar, pero los precios ya publicados no se mueven solos —
 * los recalcula una persona desde el panel ({@code RecalcularPreciosPublicados}).
 *
 * @param variable la parte proporcional, en fracción: {@code 0.0265} es el 2,65 %
 * @param fija el cargo fijo por transacción, sin IVA
 * @param iva el IVA que la pasarela cobra sobre su comisión, en fracción
 */
public record ComisionDePasarela(BigDecimal variable, Dinero fija, BigDecimal iva) {

  private static final BigDecimal MIL = BigDecimal.valueOf(1000);

  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  /** Desde este publicado se redondea a la unidad de mil; por debajo, a la centena. */
  public static final BigDecimal UMBRAL_DE_LOS_MILES = BigDecimal.valueOf(100_000);

  /** Suficiente para que el redondeo a la unidad de mil no dependa de la división. */
  private static final int ESCALA_DEL_CALCULO = 10;

  public ComisionDePasarela {
    Objects.requireNonNull(variable, "La comisión variable no puede ser nula.");
    Objects.requireNonNull(fija, "La comisión fija no puede ser nula.");
    Objects.requireNonNull(iva, "El IVA de la comisión no puede ser nulo.");
    if (variable.signum() < 0 || variable.compareTo(BigDecimal.ONE) >= 0) {
      throw new ExcepcionDeDominio(
          "La comisión variable es una fracción en [0, 1): " + variable.toPlainString());
    }
    if (iva.signum() < 0) {
      throw new ExcepcionDeDominio(
          "El IVA de la comisión no puede ser negativo: " + iva.toPlainString());
    }
    // Con la comisión variable y su IVA en el 100 % o más, ningún precio cubre la comisión: el
    // denominador de la fórmula llega a cero o se vuelve negativo.
    if (variable.multiply(BigDecimal.ONE.add(iva)).compareTo(BigDecimal.ONE) >= 0) {
      throw new ExcepcionDeDominio(
          "La comisión variable con su IVA se lleva el precio entero: "
              + variable.toPlainString()
              + " × (1 + "
              + iva.toPlainString()
              + ").");
    }
  }

  /**
   * El precio que se anuncia para que una venta por la pasarela deje por lo menos {@code contado}.
   * El redondeo es sobre el resultado: si ya es múltiplo de la unidad que le toca queda igual; un
   * contado redondo lleva la comisión encima igual que cualquiera.
   */
  public Dinero publicadoDesde(Dinero contado) {
    Objects.requireNonNull(contado, "El precio de contado no puede ser nulo.");
    BigDecimal conIva = BigDecimal.ONE.add(iva);
    BigDecimal numerador = contado.valor().add(fija.valor().multiply(conIva));
    BigDecimal denominador = BigDecimal.ONE.subtract(variable.multiply(conIva));
    // CEILING también aquí y no HALF_UP: un resultado unas diezmilésimas por encima de un múltiplo
    // de mil, redondeado hacia abajo en la división, se quedaría en ese múltiplo.
    BigDecimal exacto = numerador.divide(denominador, ESCALA_DEL_CALCULO, RoundingMode.CEILING);
    BigDecimal unidad = exacto.compareTo(UMBRAL_DE_LOS_MILES) < 0 ? CIEN : MIL;
    BigDecimal unidades = exacto.divide(unidad, 0, RoundingMode.CEILING);
    return Dinero.deCop(unidades.multiply(unidad));
  }
}
