package co.tecnosport.api.application.garantia;

import java.util.UUID;

/**
 * Reclamar la garantía de algo que no se compró en ese pedido no es un caso raro: es una variante
 * mal elegida en el panel, y dejarla pasar congelaría un término de garantía sobre una entrega que
 * nunca ocurrió.
 */
public class LineaNoEsDelPedidoException extends RuntimeException {

  public LineaNoEsDelPedidoException(UUID pedidoId, UUID varianteId) {
    super("El pedido " + pedidoId + " no tiene ninguna línea de la variante " + varianteId + ".");
  }
}
