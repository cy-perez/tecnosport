package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor.VarianteSinMargen;
import java.util.List;
import java.util.UUID;

/**
 * Las variantes agotadas porque el costo del proveedor les quitó el margen mínimo. El total viaja
 * aparte, como en {@code VariantesSinMedirRespuesta}: el aviso del panel pinta un número.
 */
public record VariantesSinMargenRespuesta(int total, List<Item> items) {

  public record Item(UUID productoId, String nombreProducto, UUID varianteId, String sku) {}

  public static VariantesSinMargenRespuesta de(List<VarianteSinMargen> variantes) {
    return new VariantesSinMargenRespuesta(
        variantes.size(),
        variantes.stream()
            .map(v -> new Item(v.productoId(), v.producto(), v.varianteId(), v.sku()))
            .toList());
  }
}
