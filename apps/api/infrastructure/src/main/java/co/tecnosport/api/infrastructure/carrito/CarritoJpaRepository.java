package co.tecnosport.api.infrastructure.carrito;

import co.tecnosport.api.infrastructure.carrito.entidad.CarritoJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarritoJpaRepository extends JpaRepository<CarritoJpaEntity, UUID> {

  /**
   * Borrado en bloque. {@code linea_carrito} tiene {@code on delete cascade} en el esquema, así que
   * las líneas se van con su carrito — un {@code delete} de JPQL no dispara la cascada de JPA, pero
   * sí la de la base de datos, que es la que está declarada aquí.
   *
   * <p>{@code clearAutomatically} y {@code flushAutomatically} juntos, no uno solo: ya costó un bug
   * real en este proyecto (ver ConfirmarRecuperacionIntegracionTest y apps/api/CLAUDE.md) que
   * {@code clearAutomatically} sin el flush descartara en silencio escrituras pendientes de la
   * misma transacción.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from CarritoJpaEntity c where c.actualizadoEn < :limite")
  int eliminarInactivosDesde(@Param("limite") Instant limite);
}
