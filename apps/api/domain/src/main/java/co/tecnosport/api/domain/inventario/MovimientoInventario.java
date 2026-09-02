package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El saldo no se edita: se agrega movimiento (docs/02-modelo-datos.md). {@code cantidad} viene con
 * signo para {@code ENTRADA}/{@code SALIDA}/{@code AJUSTE} (positivo, negativo, cualquiera de los
 * dos respectivamente) y siempre positivo para {@code RESERVA}/{@code LIBERACION}, que no afectan
 * el saldo total sino el disponible. {@code expiraEn} solo aplica a {@code RESERVA} ({@code null} =
 * no vence, caso de contraentrega). {@code referenciaId} conecta un {@code SALIDA}/{@code
 * LIBERACION} con la {@code RESERVA} que resuelve.
 */
public record MovimientoInventario(
    UUID id,
    TipoMovimientoInventario tipo,
    int cantidad,
    Instant creadoEn,
    Instant expiraEn,
    UUID referenciaId,
    String motivo) {

  public MovimientoInventario {
    Objects.requireNonNull(id, "El id del movimiento no puede ser nulo.");
    Objects.requireNonNull(tipo, "El tipo de movimiento no puede ser nulo.");
    Objects.requireNonNull(creadoEn, "La fecha del movimiento no puede ser nula.");
    if (cantidad == 0) {
      throw new ExcepcionDeDominio("La cantidad de un movimiento de inventario no puede ser cero.");
    }
    if ((tipo == TipoMovimientoInventario.ENTRADA
            || tipo == TipoMovimientoInventario.RESERVA
            || tipo == TipoMovimientoInventario.LIBERACION)
        && cantidad < 0) {
      throw new ExcepcionDeDominio("Un movimiento " + tipo + " no puede tener cantidad negativa.");
    }
    if (tipo == TipoMovimientoInventario.SALIDA && cantidad > 0) {
      throw new ExcepcionDeDominio("Un movimiento SALIDA debe tener cantidad negativa.");
    }
    if (tipo != TipoMovimientoInventario.RESERVA && expiraEn != null) {
      throw new ExcepcionDeDominio("Solo un movimiento RESERVA puede tener vencimiento.");
    }
  }
}
