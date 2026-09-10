package co.tecnosport.api.domain.reversion;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una solicitud de reversión del pago (Ley 1480 de 2011, art. 51).
 *
 * <p><b>No es un retracto con otro nombre</b>, y el modelo lo refleja: el retracto no necesita
 * motivo y lo resuelve el comercio solo; la reversión tiene causales tasadas e involucra al emisor
 * del medio de pago. Un pedido puede llegar aquí sin haber pasado nunca por un retracto, y al
 * revés.
 *
 * <p>{@code fechaDelHecho} es cuándo el comprador tuvo noticia de lo ocurrido, no cuándo compró ni
 * cuándo escribió. De ella cuelga el plazo que los términos publicados le imponen a él —cinco días
 * hábiles para solicitarla— y por eso se guarda aparte de {@code radicadaEn}.
 *
 * <p>El plazo <b>nunca bloquea</b>: quien decide es una persona con el veredicto delante, igual que
 * en el retracto. Y el veredicto se congela al radicar, porque es la foto de lo que se sabía ese
 * día.
 */
public final class SolicitudReversion {

  private static final int DIAS_HABILES_PARA_SOLICITAR = 5;

  private final UUID id;
  private final UUID solicitudId;
  private final UUID pedidoId;
  private final CausalReversion causal;
  private final Instant fechaDelHecho;
  private final Instant radicadaEn;
  private final VerdictoPlazo verdictoAlRadicar;
  private EstadoSolicitudReversion estado;
  private Instant gestionadaEn;
  private String gestionadaPor;
  private String gestion;
  private DesenlaceReversion desenlace;
  private Instant resueltaEn;
  private UUID reintegroId;

  public SolicitudReversion(
      UUID id,
      UUID solicitudId,
      UUID pedidoId,
      CausalReversion causal,
      Instant fechaDelHecho,
      Instant radicadaEn,
      VerdictoPlazo verdictoAlRadicar,
      EstadoSolicitudReversion estado,
      Instant gestionadaEn,
      String gestionadaPor,
      String gestion,
      DesenlaceReversion desenlace,
      Instant resueltaEn,
      UUID reintegroId) {
    this.id = Objects.requireNonNull(id, "El id de la solicitud no puede ser nulo.");
    this.solicitudId =
        Objects.requireNonNull(solicitudId, "La reversión necesita su solicitud de atención.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    this.causal =
        Objects.requireNonNull(
            causal, "Una reversión sin causal no se puede tramitar: son tasadas.");
    this.fechaDelHecho =
        Objects.requireNonNull(fechaDelHecho, "La fecha del hecho no puede ser nula.");
    this.radicadaEn =
        Objects.requireNonNull(radicadaEn, "La fecha de radicación no puede ser nula.");
    if (fechaDelHecho.isAfter(radicadaEn)) {
      throw new ExcepcionDeDominio("El hecho no puede ser posterior a la solicitud.");
    }
    this.verdictoAlRadicar =
        Objects.requireNonNull(verdictoAlRadicar, "El veredicto de plazo no puede ser nulo.");
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    if (estado == EstadoSolicitudReversion.GESTIONADA && gestion == null) {
      throw new ExcepcionDeDominio(
          "Una reversión gestionada necesita el registro de qué se hizo para facilitar el trámite.");
    }
    if (estado == EstadoSolicitudReversion.RESUELTA && (desenlace == null || resueltaEn == null)) {
      throw new ExcepcionDeDominio("Una reversión resuelta necesita su desenlace y su fecha.");
    }
    this.gestionadaEn = gestionadaEn;
    this.gestionadaPor = gestionadaPor;
    this.gestion = gestion;
    this.desenlace = desenlace;
    this.resueltaEn = resueltaEn;
    this.reintegroId = reintegroId;
  }

  public static SolicitudReversion radicar(
      UUID solicitudId,
      UUID pedidoId,
      CausalReversion causal,
      Instant fechaDelHecho,
      Instant ahora,
      CalendarioHabil calendario) {
    Objects.requireNonNull(calendario, "El calendario no puede ser nulo.");
    Objects.requireNonNull(fechaDelHecho, "La fecha del hecho no puede ser nula.");
    return new SolicitudReversion(
        GeneradorIdentificador.nuevo(),
        solicitudId,
        pedidoId,
        causal,
        fechaDelHecho,
        ahora,
        calendario.verdicto(
            calendario.limiteTrasDiasHabiles(fechaDelHecho, DIAS_HABILES_PARA_SOLICITAR), ahora),
        EstadoSolicitudReversion.RADICADA,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public UUID id() {
    return id;
  }

  public UUID solicitudId() {
    return solicitudId;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public CausalReversion causal() {
    return causal;
  }

  public Instant fechaDelHecho() {
    return fechaDelHecho;
  }

  public Instant radicadaEn() {
    return radicadaEn;
  }

  public VerdictoPlazo verdictoAlRadicar() {
    return verdictoAlRadicar;
  }

  public EstadoSolicitudReversion estado() {
    return estado;
  }

  public Optional<Instant> gestionadaEn() {
    return Optional.ofNullable(gestionadaEn);
  }

  public Optional<String> gestionadaPor() {
    return Optional.ofNullable(gestionadaPor);
  }

  public Optional<String> gestion() {
    return Optional.ofNullable(gestion);
  }

  public Optional<DesenlaceReversion> desenlace() {
    return Optional.ofNullable(desenlace);
  }

  public Optional<Instant> resueltaEn() {
    return Optional.ofNullable(resueltaEn);
  }

  public Optional<UUID> reintegroId() {
    return Optional.ofNullable(reintegroId);
  }

  /**
   * Deja escrito qué se hizo para facilitar el trámite, que es lo que los términos publicados
   * prometen. Sin texto no hay gestión: "se gestionó" a secas no demuestra nada el día que alguien
   * lo discuta.
   */
  public void registrarGestion(String gestion, Instant ahora, String actor) {
    if (gestion == null || gestion.isBlank()) {
      throw new ExcepcionDeDominio(
          "Facilitar el trámite exige dejar escrito qué se hizo, no solo marcarlo.");
    }
    if (actor == null || actor.isBlank()) {
      throw new ExcepcionDeDominio("Quien gestiona no puede quedar en blanco.");
    }
    if (estado != EstadoSolicitudReversion.RADICADA) {
      throw new ExcepcionDeDominio("Una solicitud " + estado + " ya no admite registrar gestión.");
    }
    this.gestion = gestion;
    this.gestionadaEn = Objects.requireNonNull(ahora, "La fecha de gestión no puede ser nula.");
    this.gestionadaPor = actor;
    this.estado = EstadoSolicitudReversion.GESTIONADA;
  }

  /**
   * {@code reintegroId} es obligatorio con {@code REINTEGRADO_DIRECTAMENTE} y tiene que faltar con
   * los demás desenlaces: cuando revierte el emisor, el dinero vuelve por la red de pagos y este
   * sistema no movió un peso — inventarle una constancia sería registrar un pago que no hicimos.
   */
  public void resolver(DesenlaceReversion desenlace, UUID reintegroId, Instant ahora) {
    Objects.requireNonNull(desenlace, "El desenlace no puede ser nulo.");
    if (estado == EstadoSolicitudReversion.RESUELTA) {
      throw new ExcepcionDeDominio("Esta solicitud de reversión ya se resolvió.");
    }
    boolean pagamosNosotros = desenlace == DesenlaceReversion.REINTEGRADO_DIRECTAMENTE;
    if (pagamosNosotros && reintegroId == null) {
      throw new ExcepcionDeDominio(
          "Una reversión resuelta devolviendo el dinero necesita el id de su constancia.");
    }
    if (!pagamosNosotros && reintegroId != null) {
      throw new ExcepcionDeDominio(
          "Solo el desenlace en que el comercio devuelve el dinero apunta a una constancia.");
    }
    this.desenlace = desenlace;
    this.reintegroId = reintegroId;
    this.resueltaEn = Objects.requireNonNull(ahora, "La fecha de resolución no puede ser nula.");
    this.estado = EstadoSolicitudReversion.RESUELTA;
  }
}
