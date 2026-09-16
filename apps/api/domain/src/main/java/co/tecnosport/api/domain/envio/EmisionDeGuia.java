package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El intento de emitirle las guías a un pedido (adr/0033).
 *
 * <p>Existe por una sola razón, y es que <strong>la plataforma cobra antes de que haya
 * guía</strong>. {@code POST /shipments} responde {@code 202} con {@code payment_status: paid} y
 * {@code master_tracking_number: null}; el número aparece entre veinticinco segundos y varios
 * minutos después, o no aparece nunca y el envío muere en {@code error}
 * (docs/13-skydropx-capacidades.md §6.10). Sin guardar ese intento, un reinicio del servicio entre
 * el cobro y la respuesta deja una guía pagada que nadie sabe que existe.
 *
 * <p>No es el {@link Envio}: el envío nace cuando ya hay guías, y aquí todavía no las hay. Tampoco
 * mueve el pedido — el pedido se queda en {@code EN_PREPARACION} hasta que las guías vivan, que es
 * lo que hace que una emisión fallida no tenga nada que devolver.
 *
 * <p>Guarda los identificadores de la plataforma y no las guías. Las guías son del {@link Envio}, y
 * el mismo dato en dos agregados son dos verdades capaces de divergir. Lo que estos identificadores
 * garantizan es que <strong>nada de lo que se pagó se pierde de vista</strong>, incluso cuando la
 * emisión sale {@link EstadoEmision#PARCIAL} y no llega a haber despacho.
 */
public final class EmisionDeGuia {

  private final UUID id;
  private final UUID pedidoId;
  private final String transportadora;
  private final String idTarifa;
  private final List<String> enviosEnPlataforma;
  private final Instant solicitadaEn;
  private EstadoEmision estado;
  private String detalle;
  private Instant resueltaEn;

  public EmisionDeGuia(
      UUID id,
      UUID pedidoId,
      String transportadora,
      String idTarifa,
      List<String> enviosEnPlataforma,
      Instant solicitadaEn,
      EstadoEmision estado,
      String detalle,
      Instant resueltaEn) {
    this.id = Objects.requireNonNull(id, "El id de la emisión no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    if (transportadora == null || transportadora.isBlank()) {
      throw new ExcepcionDeDominio("La transportadora de la emisión no puede estar vacía.");
    }
    this.transportadora = transportadora.trim();
    if (idTarifa == null || idTarifa.isBlank()) {
      throw new ExcepcionDeDominio("La tarifa con la que se emitió no puede estar vacía.");
    }
    this.idTarifa = idTarifa.trim();
    if (enviosEnPlataforma == null || enviosEnPlataforma.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Una emisión aceptada tiene que traer al menos un envío de la plataforma.");
    }
    this.enviosEnPlataforma = List.copyOf(enviosEnPlataforma);
    this.solicitadaEn =
        Objects.requireNonNull(solicitadaEn, "La fecha de la solicitud no puede ser nula.");
    this.estado = Objects.requireNonNull(estado, "El estado de la emisión no puede ser nulo.");
    this.detalle = detalle;
    this.resueltaEn = resueltaEn;
  }

  /**
   * Nace en curso, que es lo único que se sabe cuando la plataforma responde {@code 202}: los
   * envíos existen y están cobrados, y las guías no están.
   */
  public static EmisionDeGuia solicitada(
      UUID pedidoId,
      String transportadora,
      String idTarifa,
      List<String> enviosEnPlataforma,
      Instant ahora) {
    return new EmisionDeGuia(
        GeneradorIdentificador.nuevo(),
        pedidoId,
        transportadora,
        idTarifa,
        enviosEnPlataforma,
        ahora,
        EstadoEmision.EN_CURSO,
        null,
        null);
  }

  /**
   * Cierra el intento. El estado tiene que ser uno de los resueltos: una emisión no "se resuelve a
   * en curso", y aceptar {@link EstadoEmision#EN_CURSO} aquí dejaría el {@code resueltaEn} puesto
   * sobre algo que sigue abierto.
   *
   * <p>Idempotente por diseño, como {@code Envio.conciliarRecaudo}: los dos disparadores de la
   * resolución —la tarea programada y el webhook— pueden llegar al mismo tiempo, y el segundo no
   * puede reescribir lo que decidió el primero.
   */
  public void resolver(EstadoEmision estadoFinal, String detalle, Instant ahora) {
    Objects.requireNonNull(estadoFinal, "El estado final no puede ser nulo.");
    Objects.requireNonNull(ahora, "La fecha de resolución no puede ser nula.");
    if (!estadoFinal.resuelta()) {
      throw new ExcepcionDeDominio("Una emisión no se puede resolver como " + estadoFinal + ".");
    }
    if (estado.resuelta()) {
      throw new ExcepcionDeDominio("Esta emisión ya se resolvió como " + estado + ".");
    }
    this.estado = estadoFinal;
    this.detalle = detalle == null || detalle.isBlank() ? null : detalle.trim();
    this.resueltaEn = ahora;
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  /**
   * El nombre de la transportadora tal como se le muestra a una persona — "Servientrega", "99
   * minutes"—, y se guarda aquí porque <strong>la respuesta del envío no lo trae</strong>: trae
   * {@code carrier_name}, que es el código de la plataforma, y ningún nombre visible
   * (docs/13-skydropx-capacidades.md §6.10). El nombre sale de la tarifa que se eligió al emitir, y
   * quien resuelve la emisión minutos después ya no tiene esa tarifa a mano.
   *
   * <p>Derivarlo del código está descartado desde §6.8: no se puede, "99 minutes" es {@code
   * ninetynineminutes}. Y usar el código como nombre pondría "servientrega" en el correo del
   * comprador.
   */
  public String transportadora() {
    return transportadora;
  }

  /**
   * La tarifa con la que se emitió. Es además la llave de idempotencia de Skydropx: {@code
   * unique_shipment} cachea la respuesta por {@code rate_id} durante 96 horas, así que repetir la
   * creación con esta misma tarifa devuelve los mismos envíos en vez de crear otros y cobrar dos
   * veces (docs/13-skydropx-capacidades.md §6.2).
   */
  public String idTarifa() {
    return idTarifa;
  }

  /**
   * Los envíos que la plataforma creó. Son varios cuando el pedido va en varios bultos: ninguna
   * transportadora colombiana admite multipaquete y la tarifa pasa a {@code multishipment}, que
   * crea un envío —y una guía, y un cobro— por bulto (adr/0031).
   */
  public List<String> enviosEnPlataforma() {
    return enviosEnPlataforma;
  }

  public Instant solicitadaEn() {
    return solicitadaEn;
  }

  public EstadoEmision estado() {
    return estado;
  }

  /** Por qué terminó así, cuando terminó mal. En {@code EMITIDA} no hay nada que contar. */
  public Optional<String> detalle() {
    return Optional.ofNullable(detalle);
  }

  public Optional<Instant> resueltaEn() {
    return Optional.ofNullable(resueltaEn);
  }
}
