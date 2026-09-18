package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioAvisosDeRevision;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RepositorioAvisosJpa implements RepositorioAvisosDeRevision {

  /**
   * Una sola sentencia, con el mismo criterio que {@code RepositorioEnviosJpa.SQL_INSERTAR_EVENTO}:
   * el {@code on conflict} de Postgres serializa por fila sin bloqueo pesimista explícito.
   *
   * <p>El {@code where} del {@code do update} es lo que hace la diferencia entre avisar una vez y
   * avisar cada vuelta: si la fila que ya está es igual o más nueva que la novedad, no se actualiza
   * nada y {@code update} devuelve 0. Escrito como un {@code select} y después un {@code insert},
   * dos instancias leerían las dos "no se ha avisado" y mandarían las dos.
   */
  private static final String SQL_RECLAMAR =
      """
      insert into aviso_revision (tipo, referencia, avisado_en)
      values (:tipo, :referencia, :ahora)
      on conflict (tipo, referencia) do update set avisado_en = :ahora
      where aviso_revision.avisado_en < :novedad
      """;

  /** Ver {@code RepositorioAvisosDeRevision.liberarAviso}: se borra, no se restaura. */
  private static final String SQL_LIBERAR =
      """
      delete from aviso_revision where tipo = :tipo and referencia = :referencia
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioAvisosJpa(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public boolean reclamarAviso(
      TipoDeRevision tipo, UUID referencia, Instant novedad, Instant ahora) {
    Objects.requireNonNull(tipo, "El tipo de lo revisado no puede ser nulo.");
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(novedad, "La novedad no puede ser nula.");
    Objects.requireNonNull(ahora, "La fecha del aviso no puede ser nula.");

    Map<String, Object> parametros = new HashMap<>();
    parametros.put("tipo", tipo.name());
    parametros.put("referencia", referencia);
    parametros.put("novedad", Timestamp.from(novedad));
    parametros.put("ahora", Timestamp.from(ahora));
    return jdbc.update(SQL_RECLAMAR, parametros) == 1;
  }

  @Override
  public void liberarAviso(TipoDeRevision tipo, UUID referencia) {
    Objects.requireNonNull(tipo, "El tipo de lo revisado no puede ser nulo.");
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");

    Map<String, Object> parametros = new HashMap<>();
    parametros.put("tipo", tipo.name());
    parametros.put("referencia", referencia);
    jdbc.update(SQL_LIBERAR, parametros);
  }
}
