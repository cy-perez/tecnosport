package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.UUID;

/**
 * Las tres cifras de una variante, puestas una al lado de la otra a propósito:
 *
 * <ul>
 *   <li>{@code existenciaDeclarada} — lo que dice el catálogo, y por tanto lo que ve quien compra.
 *   <li>{@code saldoTotal} — lo que dice el libro de movimientos, que es la verdad.
 *   <li>{@code disponible} — el saldo menos lo reservado por pedidos en vuelo, que es lo que de
 *       verdad se puede vender ahora mismo.
 * </ul>
 *
 * <p>Que las dos primeras puedan diferir es el defecto que adr/0049 documenta y no resuelve: la
 * columna del catálogo solo la mueve el alta de la variante y este ajuste, así que <b>cada venta la
 * separa del libro</b>. {@link #descuadrada()} lo hace visible en vez de esconderlo — mientras esa
 * marca aparezca sola después de vender, el argumento para calcular la existencia desde el libro
 * sigue creciendo.
 */
public record ExistenciaDeVariante(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto,
    int existenciaDeclarada,
    int saldoTotal,
    int disponible) {

  public boolean descuadrada() {
    return existenciaDeclarada != saldoTotal;
  }

  public int reservadas() {
    return saldoTotal - disponible;
  }
}
