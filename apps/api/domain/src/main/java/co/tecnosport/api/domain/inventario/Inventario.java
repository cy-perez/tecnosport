package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El saldo de una variante no es una columna que se actualiza: es la suma de su histórico de {@link
 * MovimientoInventario}, inmutable y de solo-agregar (docs/02-modelo-datos.md). Una reserva no es
 * una entidad aparte con estado propio: es un movimiento {@code RESERVA}, y confirmarla o liberarla
 * agrega un movimiento nuevo que la referencia por id — encontrar si sigue vigente es una consulta
 * sobre el histórico, no un campo que alguien tenga que recordar sincronizar.
 */
public final class Inventario {

  private final UUID id;
  private final UUID varianteId;
  private final List<MovimientoInventario> movimientos;

  public Inventario(UUID id, UUID varianteId, List<MovimientoInventario> movimientos) {
    this.id = Objects.requireNonNull(id, "El id del inventario no puede ser nulo.");
    this.varianteId = Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    this.movimientos = new ArrayList<>(Objects.requireNonNullElse(movimientos, List.of()));
  }

  public static Inventario crear(UUID varianteId) {
    return new Inventario(GeneradorIdentificador.nuevo(), varianteId, List.of());
  }

  public UUID id() {
    return id;
  }

  public UUID varianteId() {
    return varianteId;
  }

  public List<MovimientoInventario> movimientos() {
    return List.copyOf(movimientos);
  }

  /** Lo que físicamente hay, sin descontar reservas. */
  public int saldoTotal() {
    return movimientos.stream()
        .filter(
            m ->
                m.tipo() == TipoMovimientoInventario.ENTRADA
                    || m.tipo() == TipoMovimientoInventario.SALIDA
                    || m.tipo() == TipoMovimientoInventario.AJUSTE)
        .mapToInt(MovimientoInventario::cantidad)
        .sum();
  }

  /** Lo que se puede vender ahora mismo: el total menos las reservas vigentes. */
  public int saldoDisponible(Instant ahora) {
    int reservado =
        movimientos.stream()
            .filter(m -> m.tipo() == TipoMovimientoInventario.RESERVA)
            .filter(m -> reservaVigente(m, ahora))
            .mapToInt(MovimientoInventario::cantidad)
            .sum();
    return saldoTotal() - reservado;
  }

  /**
   * {@code vigencia} nula: la reserva no vence por tiempo (contraentrega, dura hasta el despacho).
   * Con vigencia: expira en {@code ahora + vigencia} (pago en línea, 30 minutos según
   * docs/02-modelo-datos.md — la duración concreta la decide quien llama, no el dominio).
   */
  public MovimientoInventario reservar(int cantidad, Duration vigencia, Instant ahora) {
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad a reservar debe ser mayor que cero.");
    }
    int disponible = saldoDisponible(ahora);
    if (cantidad > disponible) {
      throw new ExistenciaInsuficienteException(
          "Quedan " + disponible + " unidades disponibles de la variante " + varianteId + ".");
    }
    Instant expiraEn = vigencia == null ? null : ahora.plus(vigencia);
    MovimientoInventario reserva =
        new MovimientoInventario(
            GeneradorIdentificador.nuevo(),
            TipoMovimientoInventario.RESERVA,
            cantidad,
            ahora,
            expiraEn,
            null,
            null);
    movimientos.add(reserva);
    return reserva;
  }

  /** La reserva se convierte en salida real: el pago se aprobó y la unidad ya se vendió. */
  public void confirmar(UUID idReserva, Instant ahora) {
    MovimientoInventario reserva = encontrarReserva(idReserva);
    if (estaResuelta(idReserva)) {
      throw new ReservaYaProcesadaException(idReserva);
    }
    if (reserva.expiraEn() != null && !reserva.expiraEn().isAfter(ahora)) {
      throw new ReservaYaProcesadaException(idReserva);
    }
    movimientos.add(
        new MovimientoInventario(
            GeneradorIdentificador.nuevo(),
            TipoMovimientoInventario.SALIDA,
            -reserva.cantidad(),
            ahora,
            null,
            idReserva,
            null));
  }

  /**
   * Libera aunque la reserva ya haya vencido por tiempo: venció sola o se libera explícito, el
   * efecto en el saldo es el mismo, pero el motivo queda igual en el histórico.
   */
  public void liberar(UUID idReserva, String motivo, Instant ahora) {
    MovimientoInventario reserva = encontrarReserva(idReserva);
    if (estaResuelta(idReserva)) {
      throw new ReservaYaProcesadaException(idReserva);
    }
    movimientos.add(
        new MovimientoInventario(
            GeneradorIdentificador.nuevo(),
            TipoMovimientoInventario.LIBERACION,
            reserva.cantidad(),
            ahora,
            null,
            idReserva,
            motivo));
  }

  public void registrarEntrada(int cantidad, String motivo, Instant ahora) {
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad de una entrada debe ser mayor que cero.");
    }
    movimientos.add(
        new MovimientoInventario(
            GeneradorIdentificador.nuevo(),
            TipoMovimientoInventario.ENTRADA,
            cantidad,
            ahora,
            null,
            null,
            motivo));
  }

  /**
   * {@code cantidad} puede ser negativa (pérdida, daño) o positiva (conteo físico mayor al
   * registrado).
   */
  public void registrarAjuste(int cantidad, String motivo, Instant ahora) {
    if (cantidad == 0) {
      throw new ExcepcionDeDominio("Un ajuste no puede ser de cantidad cero.");
    }
    if (saldoTotal() + cantidad < 0) {
      throw new ExcepcionDeDominio("El ajuste dejaría el saldo total en negativo.");
    }
    movimientos.add(
        new MovimientoInventario(
            GeneradorIdentificador.nuevo(),
            TipoMovimientoInventario.AJUSTE,
            cantidad,
            ahora,
            null,
            null,
            motivo));
  }

  private boolean reservaVigente(MovimientoInventario reserva, Instant ahora) {
    boolean vencida = reserva.expiraEn() != null && !reserva.expiraEn().isAfter(ahora);
    return !vencida && !estaResuelta(reserva.id());
  }

  private boolean estaResuelta(UUID idReserva) {
    return movimientos.stream()
        .anyMatch(
            m ->
                (m.tipo() == TipoMovimientoInventario.SALIDA
                        || m.tipo() == TipoMovimientoInventario.LIBERACION)
                    && idReserva.equals(m.referenciaId()));
  }

  private MovimientoInventario encontrarReserva(UUID idReserva) {
    return movimientos.stream()
        .filter(m -> m.tipo() == TipoMovimientoInventario.RESERVA && m.id().equals(idReserva))
        .findFirst()
        .orElseThrow(() -> new ReservaNoEncontradaException(idReserva));
  }
}
