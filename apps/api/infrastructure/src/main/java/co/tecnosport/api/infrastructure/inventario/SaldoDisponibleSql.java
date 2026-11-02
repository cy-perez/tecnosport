package co.tecnosport.api.infrastructure.inventario;

/**
 * {@code Inventario.saldoDisponible} escrito en SQL, para las consultas que tienen que filtrar por
 * existencia <b>dentro</b> de la base: una rejilla paginada por cursor no se puede filtrar después
 * de traer la página.
 *
 * <p><b>Es la misma regla escrita dos veces, y por eso tiene prueba propia.</b> Lo que entró, salió
 * o se ajustó, menos las reservas que siguen en pie: las que ni vencieron ({@code expira_en} nula o
 * posterior a {@code :ahora}) ni tienen una salida o una liberación que las cierre. Si una cambia y
 * la otra no, el filtro de talla ofrece lo que la ficha da por agotado; {@code
 * FiltroPorTallaJpaTest} arma los libros con el dominio y compara las dos respuestas.
 *
 * <p>Pide {@code i} como alias de {@code inventario} y el parámetro {@code :ahora}.
 */
public final class SaldoDisponibleSql {

  public static final String SALDO_DISPONIBLE =
      "(select coalesce(sum(case "
          + "when m.tipo in ('ENTRADA', 'SALIDA', 'AJUSTE') then m.cantidad "
          + "when m.tipo = 'RESERVA' and (m.expira_en is null or m.expira_en > :ahora) "
          + "  and not exists (select 1 from movimiento_inventario r "
          + "    where r.inventario_id = m.inventario_id and r.referencia_id = m.id "
          + "    and r.tipo in ('SALIDA', 'LIBERACION')) then -m.cantidad "
          + "else 0 end), 0) "
          + "from movimiento_inventario m where m.inventario_id = i.id)";

  private SaldoDisponibleSql() {}
}
