package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.util.List;
import java.util.UUID;

/** De qué configuración de la lista sale cada variante de tecnología, y su costo de hoy. */
public interface RepositorioVariantesDeProveedor {

  /** Inserta o reemplaza el de esa variante. */
  void guardar(VarianteDeProveedor vinculo);

  List<VarianteDeProveedor> deProducto(UUID productoId);

  /** Las variantes —una por color— de esa configuración de ese proveedor. */
  List<VarianteDeProveedor> deConfiguracion(UUID proveedorId, String configuracion);
}
