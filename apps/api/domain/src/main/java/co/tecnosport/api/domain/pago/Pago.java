package co.tecnosport.api.domain.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.pedido.MetodoPago;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Intento de pago contra Wompi (docs/02-modelo-datos.md), idempotente por {@link #referencia} y por
 * el {@code idEvento} de cada {@link EventoPago}: el webhook de Wompi puede repetirse
 * (docs/03-api.md) y tiene que ser inofensivo. Este agregado no conoce el {@code Pedido}: solo
 * guarda su id, para que quien orquesta la transición del pedido decida qué hacer con el nuevo
 * estado del pago.
 */
public final class Pago {

  private final UUID id;
  private final UUID pedidoId;
  private final ReferenciaPago referencia;
  private final MetodoPago metodoPago;
  private final Dinero monto;
  private final List<EventoPago> eventos;
  private final Instant creadoEn;
  private EstadoPago estado;
  private Instant actualizadoEn;
  private String idTransaccionPasarela;
  private String medioReportadoPorLaPasarela;

  public Pago(
      UUID id,
      UUID pedidoId,
      ReferenciaPago referencia,
      MetodoPago metodoPago,
      Dinero monto,
      EstadoPago estado,
      List<EventoPago> eventos,
      Instant creadoEn,
      Instant actualizadoEn,
      String idTransaccionPasarela,
      String medioReportadoPorLaPasarela) {
    this.id = Objects.requireNonNull(id, "El id del pago no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    this.referencia =
        Objects.requireNonNull(referencia, "La referencia del pago no puede ser nula.");
    this.metodoPago = Objects.requireNonNull(metodoPago, "El método de pago no puede ser nulo.");
    this.monto = Objects.requireNonNull(monto, "El monto del pago no puede ser nulo.");
    this.estado = Objects.requireNonNull(estado, "El estado del pago no puede ser nulo.");
    this.eventos = new ArrayList<>(Objects.requireNonNullElse(eventos, List.of()));
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
    this.actualizadoEn =
        Objects.requireNonNull(actualizadoEn, "La fecha de actualización no puede ser nula.");
    this.idTransaccionPasarela = idTransaccionPasarela;
    this.medioReportadoPorLaPasarela = normalizar(medioReportadoPorLaPasarela);
  }

  /** Nace {@code PENDIENTE}: crear el pago es crear el intento, antes de conocer su resultado. */
  public static Pago crear(
      UUID pedidoId,
      ReferenciaPago referencia,
      MetodoPago metodoPago,
      Dinero monto,
      Instant ahora) {
    return new Pago(
        GeneradorIdentificador.nuevo(),
        pedidoId,
        referencia,
        metodoPago,
        monto,
        EstadoPago.PENDIENTE,
        List.of(),
        ahora,
        ahora,
        null,
        null);
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public ReferenciaPago referencia() {
    return referencia;
  }

  public MetodoPago metodoPago() {
    return metodoPago;
  }

  public Dinero monto() {
    return monto;
  }

  public EstadoPago estado() {
    return estado;
  }

  public List<EventoPago> eventos() {
    return List.copyOf(eventos);
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Instant actualizadoEn() {
    return actualizadoEn;
  }

  public Optional<String> idTransaccionPasarela() {
    return Optional.ofNullable(idTransaccionPasarela);
  }

  /**
   * El medio con el que la pasarela dice que se cobró de verdad, tal como ella lo nombra ({@code
   * CARD}, {@code NEQUI}, {@code PSE}...). Crudo a propósito: es la evidencia de lo que pasó, y un
   * valor que hoy no sepamos traducir tiene que quedar guardado igual en vez de perderse en un
   * mapeo.
   *
   * <p>Vacío mientras el pago no haya recibido ni un evento ni una conciliación que lo traiga.
   */
  public Optional<String> medioReportadoPorLaPasarela() {
    return Optional.ofNullable(medioReportadoPorLaPasarela);
  }

  /**
   * Lo reporta la pasarela, así que este agregado no lo discute: se queda con lo último que dijo.
   * Un valor vacío o nulo —la pasarela no lo mandó en ese evento— no borra lo que ya se sabía.
   *
   * <p><b>No toca {@link #metodoPago()} y es deliberado.</b> Son dos hechos distintos: uno es lo
   * que el comprador eligió en el checkout y quedó en su pedido, el otro es lo que la pasarela
   * acabó cobrando. Machacar el primero con el segundo borraría la única prueba de que el sitio le
   * ofreció algo distinto de lo que le cobró.
   */
  public void registrarMedioReportadoPorLaPasarela(String medio) {
    String normalizado = normalizar(medio);
    if (normalizado != null) {
      medioReportadoPorLaPasarela = normalizado;
    }
  }

  private static String normalizar(String medio) {
    if (medio == null || medio.isBlank()) {
      return null;
    }
    return medio.trim();
  }

  /**
   * Única puerta de cambio de estado. Un {@code idEvento} ya visto no se reaplica: se ignora en
   * silencio, sin volver a validar la transición ni tocar {@link #actualizadoEn}. Devuelve si el
   * evento cambió algo, para que quien orquesta sepa si debe propagar el nuevo estado al pedido.
   */
  public boolean aplicarEvento(EventoPago evento) {
    Objects.requireNonNull(evento, "El evento no puede ser nulo.");
    boolean yaProcesado = eventos.stream().anyMatch(e -> e.idEvento().equals(evento.idEvento()));
    if (yaProcesado) {
      return false;
    }
    if (!estado.puedeTransicionarA(evento.estado())) {
      throw new TransicionDePagoInvalidaException(estado, evento.estado());
    }
    eventos.add(evento);
    estado = evento.estado();
    actualizadoEn = evento.recibidoEn();
    return true;
  }

  /**
   * Se registra una sola vez, al volver el cliente del Web Checkout de Wompi con el id de la
   * transacción en la URL de retorno (docs/11-pagos-y-envios.md): sin él, la conciliación
   * programada no tiene cómo consultar este pago en la API de Wompi, que busca por su id, no por
   * {@link #referencia}. Registrar el mismo id dos veces es inofensivo; uno distinto se rechaza,
   * porque no debería poder pasar.
   */
  public void registrarIdTransaccionPasarela(String id) {
    if (id == null || id.isBlank()) {
      throw new ExcepcionDeDominio("El id de transacción de Wompi no puede estar vacío.");
    }
    if (idTransaccionPasarela != null && !idTransaccionPasarela.equals(id)) {
      throw new ExcepcionDeDominio(
          "El pago ya tiene registrado un id de transacción de Wompi distinto.");
    }
    idTransaccionPasarela = id;
  }
}
