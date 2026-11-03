package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.BitacoraDeTareas;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bitácora de las tareas programadas en {@code ejecucion_de_tarea} ({@code V154}, ADR-0104).
 *
 * <p>Tomar es <b>una sola sentencia</b>: el {@code on conflict ... do update ... where} crea la
 * fila si la tarea nunca corrió y, si existe, solo la toma cuando le toca y nadie tiene un arriendo
 * vigente. Si dos ticks llegan a la vez, Postgres bloquea la fila para el segundo y evalúa el
 * {@code where} contra lo que dejó el primero: ve el arriendo nuevo y no devuelve nada. Es el mismo
 * razonamiento de {@link LimitadorDeIntentosJpa}, que dejó de leer y después escribir por lo mismo.
 *
 * <p>{@code REQUIRES_NEW} en las dos, y no es por costumbre: el arriendo tiene que estar confirmado
 * antes de que la tarea empiece, o una vecina no lo vería; y la transacción tiene que estar cerrada
 * mientras la tarea corre, o sus casos de uso se unirían a ella.
 */
@Component
public class BitacoraDeTareasJpa implements BitacoraDeTareas {

  private static final String TOMAR =
      """
      insert into ejecucion_de_tarea (nombre, en_curso_hasta)
      values (?1, ?3)
      on conflict (nombre) do update set en_curso_hasta = ?3
      where (ejecucion_de_tarea.terminada_en is null or ejecucion_de_tarea.terminada_en <= ?4)
        and (ejecucion_de_tarea.en_curso_hasta is null or ejecucion_de_tarea.en_curso_hasta <= ?2)
      returning nombre
      """;

  // El cast es porque un parámetro nulo no le dice al driver de qué tipo es, y Postgres no lo
  // deduce de la columna en un `set`.
  private static final String TERMINAR =
      """
      update ejecucion_de_tarea
      set terminada_en = ?2, en_curso_hasta = null, fallo = cast(?3 as text)
      where nombre = ?1
      """;

  private final EntityManager entityManager;

  public BitacoraDeTareasJpa(EntityManager entityManager) {
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tomar(String nombre, Duration intervalo, Duration arriendo, Instant ahora) {
    return !entityManager
        .createNativeQuery(TOMAR)
        .setParameter(1, nombre)
        .setParameter(2, ahora)
        .setParameter(3, ahora.plus(arriendo))
        .setParameter(4, ahora.minus(intervalo))
        .getResultList()
        .isEmpty();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void terminar(String nombre, Instant terminadaEn, String fallo) {
    entityManager
        .createNativeQuery(TERMINAR)
        .setParameter(1, nombre)
        .setParameter(2, terminadaEn)
        .setParameter(3, fallo)
        .executeUpdate();
  }
}
