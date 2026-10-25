package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los enlaces de compra de colores confirmados con el proveedor (ADR-0086). */
public interface RepositorioEnlacesDeCompra {

  void guardar(EnlaceDeCompra enlace);

  void actualizar(EnlaceDeCompra enlace);

  Optional<EnlaceDeCompra> buscarPorId(UUID id);

  /** El mismo, con la fila bloqueada hasta el commit: anular compite con el pedido que lo usa. */
  Optional<EnlaceDeCompra> buscarPorIdParaActualizar(UUID id);

  Optional<EnlaceDeCompra> buscarPorToken(String token);

  /**
   * El de ese token <b>y esa variante</b>, con la fila bloqueada hasta el commit: dos pedidos con
   * el mismo enlace no lo pueden usar los dos. La variante va en la consulta y no en un filtro
   * después: un token de otra variante no llega a bloquear nada, así que nadie puede tomar el
   * bloqueo de un enlace ajeno fuera del orden en que el pedido toma los suyos.
   */
  Optional<EnlaceDeCompra> buscarParaUsar(String token, UUID varianteId);

  /** Los creados desde {@code desde}, los más nuevos primero: la bandeja del panel. */
  List<EnlaceDeCompra> listarCreadosDesde(Instant desde);
}
