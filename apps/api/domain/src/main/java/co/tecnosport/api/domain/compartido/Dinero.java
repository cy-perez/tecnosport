package co.tecnosport.api.domain.compartido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pesos colombianos. Escala 0 siempre: el peso no se fracciona en la práctica comercial. Prohibido
 * construir desde {@code double}/{@code float} a propósito: no existe ese constructor.
 */
public record Dinero(BigDecimal valor) {

  public static final String MONEDA = "COP";

  public Dinero {
    Objects.requireNonNull(valor, "El valor de Dinero no puede ser nulo.");
    valor = valor.setScale(0, RoundingMode.HALF_UP);
    if (valor.signum() < 0) {
      throw new DineroInvalidoException("Dinero no puede ser negativo: " + valor);
    }
  }

  public static Dinero deCop(BigDecimal valor) {
    return new Dinero(valor);
  }

  public static Dinero deCop(long valorEntero) {
    return new Dinero(BigDecimal.valueOf(valorEntero));
  }
}
