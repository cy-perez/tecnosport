package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Sku;

/**
 * El SKU es único en todo el catálogo, no solo dentro de su producto (docs/02-modelo-datos.md).
 * Distinta de {@code SkuDuplicadoException} del dominio, que solo compara contra las variantes que
 * el agregado {@code Producto} ya tiene cargadas en memoria — no puede ver el resto del catálogo.
 */
public final class SkuYaEnUsoException extends RuntimeException {

  public SkuYaEnUsoException(Sku sku) {
    super("El SKU '" + sku.valor() + "' ya está en uso.");
  }
}
