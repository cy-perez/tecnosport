package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioAvisosDeSobrecosto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RepositorioAvisosDeSobrecostoJpa implements RepositorioAvisosDeSobrecosto {

  /**
   * Una sola sentencia, mismo criterio que {@link RepositorioAvisosJpa}: el {@code on conflict} de
   * Postgres serializa por fila sin bloqueo pesimista explícito, y escrito como un {@code select} y
   * después un {@code insert} dos instancias leerían las dos "no se ha avisado" y mandarían las
   * dos.
   *
   * <p><strong>{@code do nothing} y no {@code do update}</strong>, que es la diferencia con el
   * aviso de la bandeja: allá una novedad posterior tiene que volver a armar el aviso porque la
   * fila sigue viva y puede empeorar. Un cobro extra ya ocurrió y no cambia; si la transportadora
   * reliquida por otra cifra, eso llega con otra clave y entra como una fila nueva.
   */
  private static final String SQL_RECLAMAR =
      """
      insert into aviso_sobrecosto (clave, avisado_en)
      values (:clave, :ahora)
      on conflict (clave) do nothing
      """;

  private static final String SQL_LIBERAR =
      """
      delete from aviso_sobrecosto where clave = :clave
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioAvisosDeSobrecostoJpa(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public boolean reclamarAviso(String clave, Instant ahora) {
    Objects.requireNonNull(clave, "La clave del sobrecosto no puede ser nula.");
    Objects.requireNonNull(ahora, "La fecha del aviso no puede ser nula.");

    Map<String, Object> parametros = new HashMap<>();
    parametros.put("clave", clave);
    parametros.put("ahora", Timestamp.from(ahora));
    return jdbc.update(SQL_RECLAMAR, parametros) == 1;
  }

  @Override
  public void liberarAviso(String clave) {
    Objects.requireNonNull(clave, "La clave del sobrecosto no puede ser nula.");
    jdbc.update(SQL_LIBERAR, Map.of("clave", clave));
  }
}
