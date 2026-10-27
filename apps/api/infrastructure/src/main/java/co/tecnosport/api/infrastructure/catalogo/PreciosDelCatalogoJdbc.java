package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.PreciosDeVariante;
import co.tecnosport.api.application.catalogo.PreciosDelCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Por SQL y no por las entidades: el recálculo recorre el catálogo entero y solo toca dos columnas,
 * y la transacción que lo envuelve no ha cargado ninguna {@code VarianteJpaEntity} que pudiera
 * quedarse con el precio viejo.
 */
@Repository
public class PreciosDelCatalogoJdbc implements PreciosDelCatalogo {

  private final NamedParameterJdbcTemplate jdbc;

  public PreciosDelCatalogoJdbc(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PreciosDeVariante> listar() {
    return jdbc.query(
        "select v.id, v.sku, p.nombre, v.precio_contado, v.precio "
            + "from variante v "
            + "join producto p on p.id = v.producto_id "
            + "order by p.nombre, v.sku",
        new MapSqlParameterSource(),
        (rs, fila) ->
            new PreciosDeVariante(
                rs.getObject("id", UUID.class),
                rs.getString("sku"),
                rs.getString("nombre"),
                Dinero.deCop(rs.getBigDecimal("precio_contado")),
                Dinero.deCop(rs.getBigDecimal("precio"))));
  }

  @Override
  public void actualizarPublicado(UUID varianteId, Dinero publicado) {
    int filas =
        jdbc.update(
            "update variante set precio = :precio where id = :id",
            new MapSqlParameterSource()
                .addValue("precio", publicado.valor())
                .addValue("id", varianteId));
    if (filas != 1) {
      throw new IllegalStateException(
          "No existe la variante '" + varianteId + "' a la que se recalcula el precio.");
    }
  }
}
