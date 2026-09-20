package co.tecnosport.api.application.inventario;

import java.util.UUID;

/**
 * Lo que pasó al contar, con el detalle suficiente para que el panel lo pueda decir y para que
 * quien lea el registro del servidor entienda qué se corrigió.
 *
 * <p>{@code sinCambios} no es un error: contar lo mismo que ya había es el resultado normal —y
 * deseable— de un conteo. No se escribe ningún movimiento, porque un {@code AJUSTE} de cero no
 * existe (lo prohíbe {@code MovimientoInventario}) y porque un libro lleno de "no pasó nada" es un
 * libro que nadie lee.
 *
 * <p>{@code unidadesReservadas} es lo que hay comprometido en pedidos en vuelo en el momento del
 * conteo. Si el conteo queda por debajo, {@link #dejaReservasSinRespaldo()} es cierto: hay pedidos
 * aceptados que no se van a poder despachar. No se rechaza —la realidad es la que es, y prohibir la
 * corrección solo consigue que la base siga mintiendo— pero tiene que decirse en voz alta.
 */
public record ResultadoDeAjuste(
    UUID varianteId,
    String sku,
    String nombreProducto,
    int saldoAnterior,
    int saldoNuevo,
    int unidadesReservadas) {

  /** Con signo: positivo si aparecieron unidades, negativo si faltaban. */
  public int diferencia() {
    return saldoNuevo - saldoAnterior;
  }

  public boolean sinCambios() {
    return diferencia() == 0;
  }

  public boolean dejaReservasSinRespaldo() {
    return saldoNuevo < unidadesReservadas;
  }
}
