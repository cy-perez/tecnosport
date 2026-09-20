package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.UUID;

/**
 * Las dos cifras de una variante, puestas una al lado de la otra a propósito:
 *
 * <ul>
 *   <li>{@code saldoTotal} — lo que hay físicamente según el libro de movimientos.
 *   <li>{@code disponible} — el saldo menos lo reservado por pedidos en vuelo, que es lo que de
 *       verdad se puede vender ahora mismo, y lo que la vitrina publica como disponibilidad.
 * </ul>
 *
 * <p>Eran tres hasta adr/0050. La que falta, {@code existenciaDeclarada}, era la columna del
 * catálogo, y esta pantalla nació marcando cuándo se había separado del libro — cosa que pasaba con
 * cada venta. Al borrarse la columna, el descuadre dejó de existir porque dejó de haber dos números
 * que pudieran discrepar.
 */
public record ExistenciaDeVariante(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto,
    int saldoTotal,
    int disponible) {

  public int reservadas() {
    return saldoTotal - disponible;
  }
}
