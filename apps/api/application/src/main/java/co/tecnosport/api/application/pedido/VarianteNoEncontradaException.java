package co.tecnosport.api.application.pedido;

import java.util.UUID;

/**
 * Cubre tres casos que el comprador no debe distinguir: la variante no existe, su producto no está
 * publicado, o la variante está inactiva — igual que {@code ProductoNoEncontradoException} no
 * distingue "no existe" de "está en borrador".
 */
public final class VarianteNoEncontradaException extends RuntimeException {

  public VarianteNoEncontradaException(UUID varianteId) {
    super("No existe una variante vendible con id " + varianteId + ".");
  }
}
