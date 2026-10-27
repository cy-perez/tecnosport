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
   * Deja el color de cada variante por confirmar con el proveedor —{@code true}— o ya sabido
   * (ADR-0086). Lo deciden la lista y la aprobación del borrador de tecnología; la variante no sabe
   * nada de listas.
   */
  void marcarDisponibilidadesPorConfirmar(Map<UUID, Boolean> porConfirmarPorVariante);

  /**
   * Si el precio de contado de cada variante, por id, deja el margen mínimo sobre el costo nuevo
   * del proveedor (27 de octubre de 2026). La que no lo deja queda agotada por el proveedor y
   * marcada como agotada <b>por el margen</b>; la que lo deja vuelve a la venta solo si fue el
   * margen el que la agotó —una prenda que el proveedor agotó por su SKU sigue agotada—.
   *
   * <p>Y al revés: {@link #cambiarDisponibilidadDeVariantes} con {@code aLaVenta} no devuelve a la
   * venta una variante agotada por el margen, porque volver a anunciar el SKU no cambia el costo.
   */
  void marcarMargenDeVariantes(Map<UUID, Boolean> alcanzaPorVariante);

  /**
   * Las variantes que siguen agotadas porque el costo del proveedor les quitó el margen: el aviso
   * del panel. Ordenadas por producto.
   */
  List<VarianteSinMargen> variantesSinMargen();

  /** Una variante agotada por el margen, con lo que el panel necesita para nombrarla. */
  record VarianteSinMargen(UUID productoId, String producto, UUID varianteId, String sku) {
    public VarianteSinMargen {
      Objects.requireNonNull(productoId);
      Objects.requireNonNull(producto);
      Objects.requireNonNull(varianteId);
      Objects.requireNonNull(sku);
    }
  }

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
