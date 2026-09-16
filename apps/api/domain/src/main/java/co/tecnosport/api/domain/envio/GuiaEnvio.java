package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una guía: un paquete, una transportadora, un cobro y su propio rastro de eventos (adr/0031).
 *
 * <p>Existe porque <strong>ninguna transportadora colombiana admite multipaquete</strong> —los
 * siete servicios de la cuenta dicen {@code multi_packages_enabled: false}
 * (docs/13-skydropx-capacidades.md §6.3)— y la regla de un bulto por variante ya está tomada. Un
 * pedido de dos variantes son dos guías, y cada una se mueve sola: se recoge, se entrega o se
 * devuelve por su cuenta, y se cobra aparte.
 *
 * <p>El costo va aquí y no en el {@code Envio} por eso mismo: con dos bultos la cotización cambia a
 * {@code multishipment} y cobra el doble. Sumar los costos en el envío es leer el margen del
 * pedido; repartirlo a mano sería inventarlo.
 */
public final class GuiaEnvio {

  private final UUID id;
  private final String transportadora;
  private final String numero;
  private final Dinero costo;
  private final List<EventoSeguimiento> eventos;

  public GuiaEnvio(
      UUID id,
      String transportadora,
      String numero,
      Dinero costo,
      List<EventoSeguimiento> eventos) {
    this.id = Objects.requireNonNull(id, "El id de la guía no puede ser nulo.");
    if (transportadora == null || transportadora.isBlank()) {
      throw new ExcepcionDeDominio("La transportadora no puede estar vacía.");
    }
    this.transportadora = transportadora;
    if (numero == null || numero.isBlank()) {
      throw new ExcepcionDeDominio("La guía no puede estar vacía.");
    }
    this.numero = numero;
    this.costo = Objects.requireNonNull(costo, "El costo de la guía no puede ser nulo.");
    this.eventos = new ArrayList<>(Objects.requireNonNullElse(eventos, List.of()));
  }

  public static GuiaEnvio crear(String transportadora, String numero, Dinero costo) {
    return new GuiaEnvio(GeneradorIdentificador.nuevo(), transportadora, numero, costo, List.of());
  }

  /**
   * Registra un movimiento del paquete. <strong>Append-only</strong>: nada se sobrescribe y nada se
   * borra (adr/0022).
   *
   * <p>Idempotente por el identificador del evento en la plataforma, que es lo que hace inofensivo
   * un reintento del webhook: el mismo evento dos veces se guarda una. Devuelve si el evento era
   * nuevo, porque de eso depende que el caso de uso mueva o no el pedido — aplicar dos veces un
   * {@code ENTREGADO} reabriría plazos legales que ya estaban corriendo.
   *
   * <p>No valida el orden. Las transportadoras mandan eventos desordenados y con retraso, y
   * rechazar uno "viejo" sería perder justo el que faltaba para entender qué pasó.
   */
  public boolean registrarEvento(EventoSeguimiento evento) {
    Objects.requireNonNull(evento, "El evento no puede ser nulo.");
    boolean yaEstaba = eventos.stream().anyMatch(e -> e.idExterno().equals(evento.idExterno()));
    if (yaEstaba) {
      return false;
    }
    eventos.add(evento);
    return true;
  }

  /** En el orden en que ocurrieron, no en el que llegaron. */
  public List<EventoSeguimiento> eventos() {
    return eventos.stream().sorted(Comparator.comparing(EventoSeguimiento::ocurrioEn)).toList();
  }

  /**
   * El último estado conocido de este paquete, o vacío si todavía no hay eventos — una guía recién
   * emitida, o una cuyo webhook no ha llegado.
   */
  public Optional<EstadoEnvio> ultimoEstado() {
    return eventos().stream()
        .reduce((primero, siguiente) -> siguiente)
        .map(EventoSeguimiento::estado);
  }

  /**
   * ¿La historia de este paquete terminó? De un entregado, devuelto, cancelado o destruido no va a
   * llegar nada más, y la conciliación deja de preguntar por él.
   *
   * <p>Se pregunta por guía y no por envío a propósito: con dos guías, una entregada y otra en
   * tránsito, dar el envío por terminado dejaría la segunda sin conciliar para siempre.
   */
  public boolean terminada() {
    return ultimoEstado().map(EstadoEnvio::esTerminal).orElse(false);
  }

  public UUID id() {
    return id;
  }

  public String transportadora() {
    return transportadora;
  }

  public String numero() {
    return numero;
  }

  public Dinero costo() {
    return costo;
  }
}
