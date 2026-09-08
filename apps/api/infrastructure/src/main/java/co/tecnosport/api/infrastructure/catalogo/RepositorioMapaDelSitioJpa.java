package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.EntradaMapaDelSitio;
import co.tecnosport.api.application.catalogo.RepositorioMapaDelSitio;
import co.tecnosport.api.domain.compartido.Slug;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link RepositorioMapaDelSitio}.
 *
 * <p>SQL directo y no JPA a propósito: se piden dos columnas de una tabla y el resultado no es un
 * agregado. Pasar por la entidad traería el producto entero para descartarlo, y el mapeador de
 * catálogo hidrata variantes e imágenes que aquí sobran.
 *
 * <p>Ordenado por {@code actualizado_en} descendente: si algún día el catálogo pasa del tope y hay
 * que cortar, lo que se queda fuera es lo más viejo, no una franja arbitraria del alfabeto.
 */
@Repository
public class RepositorioMapaDelSitioJpa implements RepositorioMapaDelSitio {

  private static final String SQL =
      """
      select slug, actualizado_en
      from producto
      where estado = 'PUBLICADO'
      order by actualizado_en desc
      limit :limite
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioMapaDelSitioJpa(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<EntradaMapaDelSitio> listarProductosPublicados(int limite) {
    return jdbc.query(
        SQL,
        Map.of("limite", limite),
        (rs, fila) ->
            new EntradaMapaDelSitio(
                new Slug(rs.getString("slug")),
                rs.getObject("actualizado_en", Timestamp.class).toInstant()));
  }
}
