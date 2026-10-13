package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  /**
   * Las prendas de los productos de este proveedor que llevan este SKU: las de un álbum, cuyo SKU
   * quedó en sus variantes al aprobarlo (13 de octubre de 2026). Estén a la venta o agotadas. Casi
   * siempre una; dos si el mismo álbum se aprobó dos veces.
   */
  List<PrendaDeProveedor> buscarPrendasPorReferencia(UUID proveedorId, String referencia);

  /** Deja el SKU del proveedor en cada una de estas variantes, por id. */
  void asignarReferencias(Map<UUID, String> referenciaPorVariante);

  /**
   * Marca estas variantes agotadas por el proveedor —agotó su SKU— o las devuelve a la venta. Una
   * agotada sigue activa: se ve en la ficha sin disponibilidad, no se puede comprar, y un pedido ya
   * pagado de ella se despacha.
   */
  void cambiarDisponibilidadDeVariantes(List<UUID> varianteIds, boolean aLaVenta);

  /**
   * Las variantes de un producto que llevan un mismo SKU del proveedor: una por talla.
   *
   * @param retirada todas están agotadas por el proveedor
   * @param otrasALaVenta cuántas variantes del producto, de otras prendas, siguen a la venta: sin
   *     ninguna, retirar esta es agotar el producto
   */
  record PrendaDeProveedor(
      UUID productoId, List<UUID> variantes, boolean retirada, int otrasALaVenta) {
    public PrendaDeProveedor {
      Objects.requireNonNull(productoId);
      variantes = List.copyOf(variantes);
    }
  }
}
