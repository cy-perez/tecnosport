package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.ArchivoDeIngestaEnLista;
import co.tecnosport.api.application.proveedores.ArchivosDeIngestaPaginados;
import co.tecnosport.api.application.proveedores.RepositorioArchivosDeIngesta;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL y no una entidad JPA: el historial es un agrupado por key contra los lotes, y la escritura un
 * {@code upsert} de una fila. Una entidad solo añadiría el mapeo de ida y vuelta sin quitar el SQL.
 */
@Repository
public class RepositorioArchivosDeIngestaJdbc implements RepositorioArchivosDeIngesta {

  /** Los mismos estados que {@code LoteIngesta.estaAbierto}, como en el repositorio de lotes. */
  private static final List<String> ESTADOS_ABIERTOS =
      List.of(
          EstadoLote.RECIBIDO.name(),
          EstadoLote.PROCESANDO.name(),
          EstadoLote.PAUSADO.name(),
          EstadoLote.DETENIENDO.name());

  private static final String COLUMNAS =
      "a.id, a.referencia, a.proveedor_id, a.nombre_original, a.tamano_bytes, a.subido_en,"
          + " a.borrado_en";

  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioArchivosDeIngestaJdbc(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  /**
   * Primero se intenta como actualización, porque un {@code on conflict} solo admite una
   * restricción y aquí hay dos: el id y la key. La de la key no puede revertir nada —una violación
   * de unicidad aborta la transacción entera y se llevaría el lote—, así que se ignora.
   */
  @Override
  public void guardar(ArchivoDeIngesta archivo) {
    int actualizados =
        jdbc.update(
            "update archivo_ingesta set borrado_en = :borradoEn where id = :id",
            new MapSqlParameterSource()
                .addValue("id", archivo.id())
                .addValue("borradoEn", archivo.borradoEn().map(Timestamp::from).orElse(null)));
    if (actualizados > 0) {
      return;
    }
    jdbc.update(
        "insert into archivo_ingesta"
            + " (id, referencia, proveedor_id, nombre_original, tamano_bytes, subido_en, borrado_en)"
            + " values (:id, :referencia, :proveedorId, :nombre, :tamano, :subidoEn, :borradoEn)"
            + " on conflict (referencia) do nothing",
        new MapSqlParameterSource()
            .addValue("id", archivo.id())
            .addValue("referencia", archivo.referencia())
            .addValue("proveedorId", archivo.proveedorId())
            .addValue("nombre", archivo.nombreOriginal().orElse(null))
            .addValue("tamano", archivo.tamanoBytes().orElse(null))
            .addValue("subidoEn", Timestamp.from(archivo.subidoEn()))
            .addValue("borradoEn", archivo.borradoEn().map(Timestamp::from).orElse(null)));
  }

  @Override
  public Optional<ArchivoDeIngesta> buscarPorId(UUID id) {
    return jdbc
        .query(
            "select " + COLUMNAS + " from archivo_ingesta a where a.id = :id",
            new MapSqlParameterSource("id", id),
            (fila, n) -> aDominio(fila))
        .stream()
        .findFirst();
  }

  @Override
  public ArchivosDeIngestaPaginados listar(UUID proveedorId, int pagina, int tamanoPagina) {
    MapSqlParameterSource parametros =
        new MapSqlParameterSource()
            .addValue("abiertos", ESTADOS_ABIERTOS)
            .addValue("limite", tamanoPagina)
            .addValue("desde", (long) pagina * tamanoPagina);
    // Dos textos y no un `:proveedor is null`: Postgres no sabe de qué tipo es un nulo sin
    // contexto, y el `cast` en cada consulta ensucia más que el `if`.
    String filtro = "";
    if (proveedorId != null) {
      filtro = " where a.proveedor_id = :proveedorId";
      parametros.addValue("proveedorId", proveedorId);
    }
    List<ArchivoDeIngestaEnLista> items =
        jdbc.query(
            "select "
                + COLUMNAS
                + ", count(l.id) as lotes, bool_or(l.estado in (:abiertos)) as en_uso"
                + " from archivo_ingesta a"
                + " join lote_ingesta l on l.referencia_archivo = a.referencia"
                + filtro
                + " group by a.id"
                + " order by a.subido_en desc, a.id"
                + " limit :limite offset :desde",
            parametros,
            (fila, n) ->
                new ArchivoDeIngestaEnLista(
                    aDominio(fila), fila.getInt("lotes"), fila.getBoolean("en_uso")));
    Long total =
        jdbc.queryForObject(
            "select count(*) from archivo_ingesta a"
                + (filtro.isEmpty() ? " where" : filtro + " and")
                + " exists (select 1 from lote_ingesta l where l.referencia_archivo = a.referencia)",
            parametros,
            Long.class);
    long totalArchivos = total == null ? 0 : total;
    int totalPaginas = (int) ((totalArchivos + tamanoPagina - 1) / tamanoPagina);
    return new ArchivosDeIngestaPaginados(items, pagina, totalPaginas, totalArchivos);
  }

  @Override
  public boolean enUso(String referencia) {
    Boolean enUso =
        jdbc.queryForObject(
            "select exists (select 1 from lote_ingesta"
                + " where referencia_archivo = :referencia and estado in (:abiertos))",
            new MapSqlParameterSource()
                .addValue("referencia", referencia)
                .addValue("abiertos", ESTADOS_ABIERTOS),
            Boolean.class);
    return Boolean.TRUE.equals(enUso);
  }

  private static ArchivoDeIngesta aDominio(ResultSet fila) throws SQLException {
    return new ArchivoDeIngesta(
        fila.getObject("id", UUID.class),
        fila.getString("referencia"),
        fila.getObject("proveedor_id", UUID.class),
        fila.getString("nombre_original"),
        fila.getObject("tamano_bytes", Long.class),
        instante(fila.getTimestamp("subido_en")),
        instante(fila.getTimestamp("borrado_en")));
  }

  private static Instant instante(Timestamp marca) {
    return marca == null ? null : marca.toInstant();
  }
}
