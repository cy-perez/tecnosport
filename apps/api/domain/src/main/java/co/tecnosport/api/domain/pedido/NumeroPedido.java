package co.tecnosport.api.domain.pedido;

import java.util.regex.Pattern;

/**
 * Identificador legible del pedido ({@code TS-2026-000123}, apps/api/CLAUDE.md), distinto del
 * {@code id} interno. El dominio no lo genera por sí solo: el secuencial exige una secuencia
 * atómica entre transacciones concurrentes, garantía que solo da la base de datos — por eso nace de
 * {@link #de(int, long)} con un secuencial ya reservado por infrastructure, nunca dentro de {@link
 * Pedido#crear}.
 */
public record NumeroPedido(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^TS-\\d{4}-\\d{6}$");
  private static final long SECUENCIAL_MINIMO = 1;
  private static final long SECUENCIAL_MAXIMO = 999_999;

  public NumeroPedido {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new NumeroPedidoInvalidoException(
          "El número de pedido \"" + valor + "\" no tiene el formato TS-AAAA-NNNNNN.");
    }
  }

  public static NumeroPedido de(int anio, long secuencial) {
    if (secuencial < SECUENCIAL_MINIMO || secuencial > SECUENCIAL_MAXIMO) {
      throw new NumeroPedidoInvalidoException(
          "El secuencial "
              + secuencial
              + " está fuera de rango ("
              + SECUENCIAL_MINIMO
              + " a "
              + SECUENCIAL_MAXIMO
              + ").");
    }
    return new NumeroPedido(String.format("TS-%04d-%06d", anio, secuencial));
  }
}
