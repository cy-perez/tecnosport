package co.tecnosport.api.infrastructure.catalogo;

import static co.tecnosport.api.infrastructure.inventario.SaldoDisponibleSql.SALDO_DISPONIBLE;

import co.tecnosport.api.application.catalogo.ConsultaDeTallasEnExistencia;
import co.tecnosport.api.application.catalogo.FiltroDeTalla;
import co.tecnosport.api.application.catalogo.TallaEnExistencia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Las tallas de la vitrina, leídas de la base.
 *
 * <p>Qué atributo es un eje de talla y cuándo dos escrituras son la misma talla lo decide el
 * dominio ({@link Atributo#esTalla}, {@link Atributo#tallaNormalizada}) y no el SQL: la consulta
 * trae los valores crudos, que son pocos, y la regla se aplica aquí. Copiarla a SQL —sin tildes,
 * «2XL» como «XXL», «s/m» como «S-M»— sería una segunda versión que tarde o temprano difiere. La
 * única regla que sí va en SQL es el saldo, porque sin ella no se puede paginar ({@link
 * co.tecnosport.api.infrastructure.inventario.SaldoDisponibleSql}).
 */
@Repository
public class TallasDelCatalogoJdbc implements ConsultaDeTallasEnExistencia {

  /** La variante que se puede comprar: {@code Variante.seVende} y con saldo. Pide {@code vt}. */
  static final String VARIANTE_QUE_SE_VENDE =
      "vt.estado = 'ACTIVA' and not vt.agotada_por_proveedor and " + SALDO_DISPONIBLE + " > 0";

  private final NamedParameterJdbcTemplate jdbc;
  private final Reloj reloj;

  public TallasDelCatalogoJdbc(NamedParameterJdbcTemplate jdbc, Reloj reloj) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /** Los valores de talla tal como están escritos, y los ejes en que aparecen. */
  record ValoresDeTalla(Set<UUID> ejes, Set<String> valores) {

    boolean vacios() {
      return valores.isEmpty();
    }
  }

  /**
   * El instante con que se evalúan las reservas, como parámetro {@code :ahora}. Con desfase y no
   * como {@code Instant}, que el driver de PostgreSQL no sabe a qué tipo llevar.
   */
  OffsetDateTime ahora() {
    return reloj.ahora().atOffset(ZoneOffset.UTC);
  }

  @Override
  public List<TallaEnExistencia> tallasEnExistencia() {
    MapSqlParameterSource parametros = new MapSqlParameterSource("ahora", ahora());
    String sql =
        "select distinct c.linea, a.id as atributo_id, a.nombre, a.tipo, vav.valor "
            + "from producto p "
            + "join categoria c on c.id = p.categoria_id "
            + "join variante vt on vt.producto_id = p.id "
            + "join variante_atributo_valor vav on vav.variante_id = vt.id "
            + "join atributo a on a.id = vav.atributo_id "
            + "join inventario i on i.variante_id = vt.id "
            + "where p.estado = 'PUBLICADO' and p.estado_disponibilidad = 'DISPONIBLE' "
            + "and c.linea in ('ROPA', 'CALZADO') and a.tipo <> 'COLOR' and "
            + VARIANTE_QUE_SE_VENDE;
    return jdbc.queryForList(sql, parametros).stream()
        .filter(TallasDelCatalogoJdbc::esEjeDeTalla)
        .map(
            fila ->
                new TallaEnExistencia(
                    LineaCatalogo.valueOf((String) fila.get("linea")), (String) fila.get("valor")))
        .toList();
  }

  /**
   * Las escrituras que en esa línea son la talla pedida: «m» y «M» para la M. Con ellas la búsqueda
   * filtra por igualdad, sin normalizar en SQL. Vacío si ninguna variante la tiene escrita.
   */
  ValoresDeTalla valoresDe(FiltroDeTalla filtro) {
    MapSqlParameterSource parametros = new MapSqlParameterSource("linea", filtro.linea().name());
    String sql =
        "select distinct a.id as atributo_id, a.nombre, a.tipo, vav.valor "
            + "from variante_atributo_valor vav "
            + "join atributo a on a.id = vav.atributo_id "
            + "join variante vt on vt.id = vav.variante_id "
            + "join producto p on p.id = vt.producto_id "
            + "join categoria c on c.id = p.categoria_id "
            + "where c.linea = :linea and a.tipo <> 'COLOR'";
    List<Map<String, Object>> filas =
        jdbc.queryForList(sql, parametros).stream()
            .filter(TallasDelCatalogoJdbc::esEjeDeTalla)
            .filter(
                fila ->
                    Atributo.tallaNormalizada((String) fila.get("valor")).equals(filtro.talla()))
            .toList();
    return new ValoresDeTalla(
        filas.stream().map(fila -> (UUID) fila.get("atributo_id")).collect(Collectors.toSet()),
        filas.stream().map(fila -> (String) fila.get("valor")).collect(Collectors.toSet()));
  }

  private static boolean esEjeDeTalla(Map<String, Object> fila) {
    return new Atributo(
            (UUID) fila.get("atributo_id"),
            (String) fila.get("nombre"),
            TipoAtributo.valueOf((String) fila.get("tipo")),
            List.of())
        .esTalla();
  }
}
