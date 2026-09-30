package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las consultas del catálogo que solo tienen sentido para lo que viene de un proveedor.
 *
 * <p>Aparte de {@code RepositorioProductos} y no dentro, a propósito: aquel lo implementan once
 * dobles de prueba además del adaptador, y ninguno de esos casos de uso necesita saber de huellas
 * ni de ventanas de disponibilidad. Es una consulta de lectura sobre el mismo agregado, que {@code
 * docs/01-arquitectura.md} permite cuando la proyección es incómoda para el puerto general.
 */
public interface RepositorioProductosDeProveedor {

  /**
   * El producto de este proveedor con esta huella, si ya existe. La huella es única por proveedor.
   */
  Optional<Producto> buscarPorHuella(UUID proveedorId, HuellaProveedor huella);

  /**
   * Los de origen proveedor que siguen disponibles y no se han visto desde antes de {@code limite}.
   * Es lo que el job de disponibilidad oculta; los manuales nunca salen aquí.
   */
  List<Producto> disponiblesVistosAntesDe(Instant limite);
}
