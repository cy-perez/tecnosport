package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * La del panel, y por eso no se reusa {@code pedido.VarianteNoEncontradaException}: aquella
 * confunde a propósito tres casos —no existe, su producto está en borrador, está inactiva— porque
 * al comprador no se le dice cuál. Aquí es al revés: quien administra el catálogo ve los borradores
 * y las inactivas, así que "no la encuentro" solo puede querer decir que no existe.
 *
 * <p>Mismo criterio que {@code ProductoNoEncontradoPorIdException} frente a {@code
 * ProductoNoEncontradoException}.
 */
public final class VarianteNoEncontradaPorIdException extends RuntimeException {

  public VarianteNoEncontradaPorIdException(UUID varianteId) {
    super("No existe la variante con id " + varianteId + ".");
  }
}
