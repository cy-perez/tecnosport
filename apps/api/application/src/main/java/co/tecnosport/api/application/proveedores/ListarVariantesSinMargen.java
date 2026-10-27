package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor.VarianteSinMargen;
import java.util.List;
import java.util.Objects;

/**
 * El aviso del panel de lo que dejó de venderse porque el costo del proveedor le quitó el margen
 * mínimo (27 de octubre de 2026): la lista de tecnología y la renovación de un anuncio la agotan
 * sin preguntarle a nadie, y sin el aviso nadie sabría que hay que subir el contado o esperar a que
 * el costo baje.
 */
public final class ListarVariantesSinMargen {

  private final RepositorioProductosDeProveedor productosDeProveedor;

  public ListarVariantesSinMargen(RepositorioProductosDeProveedor productosDeProveedor) {
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
  }

  public List<VarianteSinMargen> ejecutar() {
    return productosDeProveedor.variantesSinMargen();
  }
}
