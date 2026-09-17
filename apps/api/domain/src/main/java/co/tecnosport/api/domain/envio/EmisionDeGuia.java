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
 * <p><strong>Y por eso nace antes de la llamada, no después.</strong> La primera versión de esta
 * clase se construía con los identificadores que devolvía la plataforma, o sea <em>después</em> de
 * cobrar, y así no cumplía lo que este mismo javadoc prometía: entre el cobro y la respuesta no
 * había fila. Ahora nace {@link EstadoEmision#SOLICITADA} con el {@code idTarifa} —la única llave
 * que recupera un envío ya pagado, por idempotencia, durante 96 horas— y sólo entonces se llama.
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
  private final String actor;
  private final Instant solicitadaEn;
  private List<String> enviosEnPlataforma;
  private EstadoEmision estado;
  private String detalle;
  private Instant resueltaEn;

  public EmisionDeGuia(
      UUID id,
      UUID pedidoId,
      String transportadora,
      String idTarifa,
      String actor,
      List<String> enviosEnPlataforma,
      Instant solicitadaEn,
      EstadoEmision estado,
      String detalle,
      Instant resueltaEn) {
    this.id = Objects.requireNonNull(id, "El id de la emisión no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    this.transportadora = exigir(transportadora, "La transportadora de la emisión");
    this.idTarifa = exigir(idTarifa, "La tarifa con la que se emitió");
    this.actor = exigir(actor, "El actor que pidió la emisión");
    this.enviosEnPlataforma =
        List.copyOf(Objects.requireNonNullElse(enviosEnPlataforma, List.of()));
    this.solicitadaEn =
        Objects.requireNonNull(solicitadaEn, "La fecha de la solicitud no puede ser nula.");
    this.estado = Objects.requireNonNull(estado, "El estado de la emisión no puede ser nulo.");
    this.detalle = detalle;
    this.resueltaEn = resueltaEn;
    if (estado == EstadoEmision.EN_CURSO && this.enviosEnPlataforma.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Una emisión en curso tiene que traer al menos un envío de la plataforma.");
    }
  }

  /**
   * La fila que se escribe <strong>antes</strong> de llamar a la plataforma. Todavía no hay envíos
   * ni cobro; lo que hay es la intención y, sobre todo, la tarifa con la que se va a pedir.
   */
  public static EmisionDeGuia solicitar(
      UUID pedidoId, String transportadora, String idTarifa, String actor, Instant ahora) {
    return new EmisionDeGuia(
        GeneradorIdentificador.nuevo(),
        pedidoId,
        transportadora,
        idTarifa,
        actor,
        List.of(),
        ahora,
        EstadoEmision.SOLICITADA,
        null,
        null);
  }

  /**
   * La plataforma creó los envíos y cobró. A partir de aquí hay algo que releer.
   *
   * <p>Exige venir de {@link EstadoEmision#SOLICITADA}: aceptar dos veces la misma emisión
   * sobrescribiría unos identificadores pagados con otros, y los primeros se perderían de vista.
   */
  public void aceptada(List<String> envios, Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    if (envios == null || envios.isEmpty()) {
      throw new ExcepcionDeDominio("Una emisión aceptada trae al menos un envío de la plataforma.");
    }
    if (estado != EstadoEmision.SOLICITADA) {
      throw new ExcepcionDeDominio("Esta emisión ya no está solicitada, está en " + estado + ".");
    }
    this.enviosEnPlataforma = List.copyOf(envios);
    this.estado = EstadoEmision.EN_CURSO;
  }

  /**
   * La llamada no terminó y no sabemos si la plataforma cobró. Cierra el intento sin darlo por
   * fallido, que es lo único honesto: reintentar sobre esto pagaría dos veces, y el {@code
   * idTarifa} que esta fila guarda es lo que permite recuperar el envío si de verdad se creó.
   */
  public void indeterminada(String detalle, Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha no puede ser nula.");
    if (estado != EstadoEmision.SOLICITADA) {
      throw new ExcepcionDeDominio(
          "Solo una emisión solicitada queda indeterminada, no una en " + estado + ".");
    }
    this.estado = EstadoEmision.INDETERMINADA;
    this.detalle = limpiar(detalle);
    this.resueltaEn = ahora;
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
    if (!estado.abierta()) {
      throw new ExcepcionDeDominio("Esta emisión ya se resolvió como " + estado + ".");
    }
    if (estado == EstadoEmision.INDETERMINADA) {
      throw new ExcepcionDeDominio(
          "Una emisión indeterminada la resuelve una persona, no un programa: puede haber un envío"
              + " pagado del que no tenemos identificador.");
    }
    if (estado == EstadoEmision.SOLICITADA && estadoFinal != EstadoEmision.FALLIDA) {
      throw new ExcepcionDeDominio(
          "Una emisión que nunca llegó a aceptarse solo puede quedar FALLIDA, no " + estadoFinal);
    }
    this.estado = estadoFinal;
    this.detalle = limpiar(detalle);
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
   * <p>Sirve además para no repetir transportadora al reintentar: la que acaba de fallar tiene
   * buenas probabilidades de volver a fallar —el contador de remisiones de Coordinadora está
   * atascado y falla siempre— y recotizar sin excluirla la vuelve a elegir, por ser la más barata.
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
   * Quién comprometió el saldo. Para una acción que gasta dinero, una línea de registro no es
   * auditoría: el dato va en la fila.
   */
  public String actor() {
    return actor;
  }

  /**
   * Los envíos que la plataforma creó. Son varios cuando el pedido va en varios bultos: ninguna
   * transportadora colombiana admite multipaquete y la tarifa pasa a {@code multishipment}, que
   * crea un envío —y una guía, y un cobro— por bulto (adr/0031).
   *
   * <p>Vacío mientras la emisión está {@link EstadoEmision#SOLICITADA} o quedó {@link
   * EstadoEmision#INDETERMINADA}: en el primer caso todavía no se pidió, y en el segundo se pidió y
   * no se supo qué pasó.
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

  private static String exigir(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(queEs + " no puede estar vacío.");
    }
    return valor.trim();
  }

  private static String limpiar(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
