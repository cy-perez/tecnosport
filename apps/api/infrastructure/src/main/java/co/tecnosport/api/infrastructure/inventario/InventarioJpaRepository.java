package co.tecnosport.api.infrastructure.inventario;

import co.tecnosport.api.infrastructure.inventario.entidad.InventarioJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventarioJpaRepository extends JpaRepository<InventarioJpaEntity, UUID> {

  /**
   * Bloqueo pesimista sobre la fila de inventario: dos compradores simultáneos de la última unidad
   * de una variante serializan aquí, no en la variante misma (docs/02-modelo-datos.md).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InventarioJpaEntity> findByVarianteId(UUID varianteId);

  /**
   * Sin {@code @Lock}, y con otro nombre que {@link #findByVarianteId} justamente para que no lo
   * herede: esto lo llama el catálogo público en cada página que pinta, y bloquear ahí sería
   * serializar la vitrina contra el checkout.
   */
  List<InventarioJpaEntity> findAllByVarianteIdIn(Collection<UUID> varianteIds);

  /**
   * Abre el libro de una variante si nadie lo ha abierto, y no hace nada si ya existe. Nativa y no
   * un {@code save}, porque lo que la hace segura es el {@code on conflict}: dos transacciones que
   * lleguen a la vez contra {@code ux_inventario_variante} no chocan —la segunda espera a que la
   * primera confirme y entonces no inserta nada—, mientras que dos {@code save} producen una
   * violación de integridad que hay que traducir a mano.
   *
   * <p>Sin {@code clearAutomatically}: limpiar el contexto aquí descartaría en silencio cualquier
   * escritura anterior de la misma transacción (ver apps/api/CLAUDE.md).
   */
  @Modifying
  @Query(
      value =
          "insert into inventario (id, variante_id) values (:id, :varianteId)"
              + " on conflict (variante_id) do nothing",
      nativeQuery = true)
  void abrirSiNoExiste(@Param("id") UUID id, @Param("varianteId") UUID varianteId);
}
