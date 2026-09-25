package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.LineaPedidoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineaPedidoJpaRepository extends JpaRepository<LineaPedidoJpaEntity, UUID> {

  List<LineaPedidoJpaEntity> findByPedidoId(UUID pedidoId);

  void deleteByPedidoId(UUID pedidoId);

  /**
   * Si alguna de esas variantes se vendió. Lo pregunta el borrado de un producto del catálogo.
   *
   * <p>{@code exists} y no {@code count}: la consulta se resuelve con un {@code limit 1} y la
   * respuesta es la misma. Con la lista vacía, Spring Data genera un {@code in ()} que Postgres no
   * acepta, así que quien llama no la manda vacía — {@code EliminarProducto} sale antes.
   */
  boolean existsByVarianteIdIn(Collection<UUID> varianteIds);
}
