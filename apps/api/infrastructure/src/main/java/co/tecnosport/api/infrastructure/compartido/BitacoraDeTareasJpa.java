package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.application.compartido.BitacoraDeTareas;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bitácora de las tareas programadas en {@code ejecucion_de_tarea} ({@code V154}, ADR-0104).
 *
 * <p>Tomar es <b>una sola sentencia</b>. El {@code insert} crea la fila la primera vez que se ve la
 * tarea, sin arriendo: queda anotada y no se corre. Si la fila existe, el {@code on conflict ... do
 * update ... where} solo la toma cuando le toca y nadie tiene un arriendo vigente, y deja la marca
 * de quien la tomó. Lo que devuelve {@code returning} distingue los tres casos: ninguna fila (no le
 * tocaba o la tiene otro), la marca nula (la acaba de crear) o la marca de quien llamó (la tomó).
 *
 * <p>Si dos vueltas llegan a la vez, Postgres bloquea la fila para la segunda y evalúa el {@code
 * where} contra lo que dejó la primera: ve el arriendo nuevo y no devuelve nada. Es el mismo
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
      insert into ejecucion_de_tarea (nombre, tomada_en)
      values (?1, ?2)
      on conflict (nombre) do update
        set tomada_en = ?2, en_curso_hasta = ?3, arrendada_por = ?5
      where ejecucion_de_tarea.tomada_en <= ?4
        and (ejecucion_de_tarea.en_curso_hasta is null or ejecucion_de_tarea.en_curso_hasta <= ?2)
      returning cast(arrendada_por as text)
      """;

  // El cast del fallo es porque un parámetro nulo no le dice al driver de qué tipo es, y Postgres
  // no lo deduce de la columna en un `set`. Y el `where` del dueño es lo que impide que una vuelta
  // cuyo arriendo venció —un hilo que se quedó congelado más allá de su petición— suelte el de la
  // vuelta que tomó la tarea después.
  private static final String TERMINAR =
      """
      update ejecucion_de_tarea
      set terminada_en = ?3, en_curso_hasta = null, arrendada_por = null, fallo = cast(?4 as text)
      where nombre = ?1 and arrendada_por = ?2
      """;

  private final EntityManager entityManager;

  public BitacoraDeTareasJpa(EntityManager entityManager) {
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tomar(
      String nombre, Duration intervalo, Duration arriendo, UUID quien, Instant ahora) {
    List<?> filas =
        entityManager
            .createNativeQuery(TOMAR)
            .setParameter(1, nombre)
            .setParameter(2, ahora)
            .setParameter(3, ahora.plus(arriendo))
            .setParameter(4, ahora.minus(intervalo))
            .setParameter(5, quien)
            .getResultList();
    return !filas.isEmpty() && quien.toString().equals(filas.getFirst());
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void terminar(String nombre, UUID quien, Instant terminadaEn, String fallo) {
    entityManager
        .createNativeQuery(TERMINAR)
        .setParameter(1, nombre)
        .setParameter(2, quien)
        .setParameter(3, terminadaEn)
        .setParameter(4, fallo)
        .executeUpdate();
  }
}
