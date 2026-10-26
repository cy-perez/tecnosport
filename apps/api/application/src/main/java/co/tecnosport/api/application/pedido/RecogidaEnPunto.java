package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Si un carrito se puede recoger en el punto de Medellín. Desde el 26 de octubre de 2026 no basta
 * con que el negocio ofrezca la recogida ({@link ModalidadesDeEntrega}): <b>todo</b> lo que lleva
 * tiene que venir de un proveedor local y activo ({@link Proveedor#esLocal()}), que es lo que llega
 * al punto a tiempo. Un solo artículo de otro origen —un producto manual, la tecnología del
 * mayorista— la quita para el carrito entero, porque el sistema no tiene pedidos parciales.
 *
 * <p>Lo decide el servidor con los productos reales, no con lo que diga el cliente (regla 7): la
 * pantalla pregunta por aquí lo que ofrece, y {@code CrearPedido} y {@code ReintentarPago} lo
 * exigen antes de reservar nada (ADR-0093).
 */
public final class RecogidaEnPunto {

  /**
   * Una protección técnica, no un dato del negocio: cada variante cuesta una lectura del catálogo,
   * y la consulta es pública. Ningún carrito de verdad llega a tantas variantes distintas; uno que
   * llegue no se recoge, y se responde sin leer nada.
   */
  static final int MAXIMO_VARIANTES = 50;

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

  /**
   * Lo que no se vende no se puede recoger: no existe, o existe y no está a la venta. Se responde
   * igual en los dos casos para no confirmar que una variante no publicada existe ni de dónde
   * viene.
   */
  public boolean disponiblePara(Collection<UUID> varianteIds) {
    return todoLocal(varianteIds, false);
  }

  /**
   * Aquí lo que no se vende no decide por sí solo: si todo lo demás es local, el caso de uso
   * responde por ello su 404, que es lo que respondía antes de esta regla. Si además hay algo que
   * no es local, la respuesta es este 409.
   */
  public void exigirDisponible(TipoEntrega tipoEntrega, Collection<UUID> varianteIds) {
    if (tipoEntrega == TipoEntrega.RETIRO_EN_PUNTO && !todoLocal(varianteIds, true)) {
      throw new RetiroEnPuntoNoDisponibleException();
    }
  }

  private boolean todoLocal(Collection<UUID> varianteIds, boolean saltarLoQueNoSeVende) {
    if (!modalidadesDeEntrega.retiroEnPunto() || varianteIds == null || varianteIds.isEmpty()) {
      return false;
    }
    // Sin repetidos antes de leer: cada repetido de una variante local costaría una lectura más.
    Set<UUID> distintas = new LinkedHashSet<>(varianteIds);
    if (distintas.contains(null) || distintas.size() > MAXIMO_VARIANTES) {
      return false;
    }
    Map<UUID, Boolean> localPorProveedor = new HashMap<>();
    for (UUID varianteId : distintas) {
      Optional<Producto> producto = vendible(varianteId);
      if (producto.isEmpty()) {
        if (saltarLoQueNoSeVende) {
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

  /**
   * La misma pregunta que {@code ProductoVendible}, sin lanzar: publicado, en vitrina y a la venta.
   */
  private Optional<Producto> vendible(UUID varianteId) {
    return repositorioProductos
        .buscarPorVarianteId(varianteId)
        .filter(p -> p.estado() == EstadoProducto.PUBLICADO && p.estaEnVitrina())
        .filter(
            p -> p.variantes().stream().anyMatch(v -> v.id().equals(varianteId) && v.seVende()));
  }

  /** Un proveedor desactivado no surte, así que lo suyo tampoco llega al punto. */
  private boolean esLocal(UUID proveedorId) {
    return repositorioProveedores
        .buscarPorId(proveedorId)
        .map(p -> p.activo() && p.esLocal())
        .orElse(false);
  }
}
