package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

/**
 * Solo va en la respuesta cuando {@code metodoPago == TRANSFERENCIA_MANUAL}
 * (docs/11-pagos-y-envios.md). {@code referencia} es el número legible del pedido: el mismo que ya
 * identifica el pedido en todo lo demás, no una referencia aparte que inventar.
 *
 * <p><b>Era una cuenta y ahora son varias</b> (28 de septiembre de 2026): Nequi, Daviplata y una
 * cuenta de ahorros de BBVA. La referencia no se repite por cuenta porque no depende de ella —es
 * del pedido— y repetirla invitaría a pensar que cada cuenta lleva la suya.
 */
public record DatosTransferenciaRespuesta(
    List<CuentaDeTransferenciaRespuesta> cuentas, String referencia) {

  public DatosTransferenciaRespuesta {
    cuentas = cuentas == null ? List.of() : List.copyOf(cuentas);
  }
}
