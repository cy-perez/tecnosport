package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Si un carrito se puede recoger en el punto de Medellín. Desde el 26 de octubre de 2026 no basta
 * con que el negocio ofrezca la recogida ({@link ModalidadesDeEntrega}): <b>todo</b> lo que lleva
 * tiene que venir de un proveedor local ({@link Proveedor#esLocal()}), que es lo que llega al punto
 * a tiempo. Un solo artículo de otro origen —un producto manual, la tecnología del mayorista— la
 * quita para el carrito entero, porque el sistema no tiene pedidos parciales.
 *
 * <p>Lo decide el servidor con los productos reales, no con lo que diga el cliente (regla 7): la
 * pantalla pregunta por aquí lo que ofrece, y {@code CrearPedido} y {@code ReintentarPago} lo
 * exigen antes de reservar nada.
 */
public final class RecogidaEnPunto {

  private final ModalidadesDeEntrega modalidadesDeEntrega;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioProveedores repositorioProveedores;

  public RecogidaEnPunto(
      ModalidadesDeEntrega modalidadesDeEntrega,
      RepositorioProductos repositorioProductos,
      RepositorioProveedores repositorioProveedores) {
    this.modalidadesDeEntrega =
        Objects.requireNonNull(
            modalidadesDeEntrega, "Las modalidades de entrega no pueden ser nulas.");
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.repositorioProveedores =
        Objects.requireNonNull(
            repositorioProveedores, "El repositorio de proveedores no puede ser nulo.");
  }

  /** Una variante que no existe no se puede recoger: no se sabe de dónde viene. */
  public boolean disponiblePara(Collection<UUID> varianteIds) {
    return todoLocal(varianteIds, false);
  }

  /**
   * Aquí una variante que no existe no decide: el caso de uso responde por ella su 404, que es lo
   * que respondía antes de esta regla. Sin eso, un retiro con una variante inventada daba 409 y
   * confirmaba que lo demás del carrito existía.
   */
  public void exigirDisponible(TipoEntrega tipoEntrega, Collection<UUID> varianteIds) {
    if (tipoEntrega == TipoEntrega.RETIRO_EN_PUNTO && !todoLocal(varianteIds, true)) {
      throw new RetiroEnPuntoNoDisponibleException();
    }
  }

  private boolean todoLocal(Collection<UUID> varianteIds, boolean saltarDesconocidas) {
    if (!modalidadesDeEntrega.retiroEnPunto() || varianteIds == null || varianteIds.isEmpty()) {
      return false;
    }
    Map<UUID, Boolean> localPorProveedor = new HashMap<>();
    for (UUID varianteId : varianteIds) {
      Optional<Producto> producto = repositorioProductos.buscarPorVarianteId(varianteId);
      if (producto.isEmpty()) {
        if (saltarDesconocidas) {
          continue;
        }
        return false;
      }
      Optional<UUID> proveedorId = producto.get().proveedorId();
      if (proveedorId.isEmpty()
          || !localPorProveedor.computeIfAbsent(proveedorId.get(), this::esLocal)) {
        return false;
      }
    }
    return true;
  }

  private boolean esLocal(UUID proveedorId) {
    return repositorioProveedores.buscarPorId(proveedorId).map(Proveedor::esLocal).orElse(false);
  }
}
