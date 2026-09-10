package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una petición, queja, reclamo o solicitud de datos radicada en el buzón de atención.
 *
 * <p>Los términos publicados prometen "canales de orientación, asistencia y <b>radicación</b> de
 * peticiones, quejas y reclamos", y radicar es bastante más que leer un correo: es dejar constancia
 * con un número, con una fecha y con un plazo corriendo. Este agregado es esa constancia. Como
 * {@code SolicitudRetracto}, no sustituye el acto —que ocurre en el correo o en el WhatsApp que el
 * documento anuncia— sino que lo registra, y por eso guarda {@link #radicadaPor}: siempre una
 * persona del negocio.
 *
 * <p><b>Dos fechas y no una.</b> {@code recibidaEn} es cuándo llegó y {@code radicadaEn} cuándo
 * alguien la registró. El plazo corre desde la primera —si corriera desde la segunda, radicar tarde
 * sería una forma de no incumplir nunca— y la distancia entre las dos es lo único que después
 * explica por qué nadie se enteró a tiempo.
 *
 * <p>{@code pedidoId} es opcional: una consulta de datos personales no tiene por qué venir de una
 * compra, y exigir un pedido convertiría un derecho de cualquier titular en un privilegio de
 * clientes.
 */
public final class SolicitudAtencion {

  private final UUID id;
  private final NumeroRadicado numeroRadicado;
  private final TipoSolicitud tipo;
  private final CorreoElectronico correo;
  private final UUID pedidoId;
  private final Instant recibidaEn;
  private final Instant radicadaEn;
  private final String radicadaPor;
  private final String asunto;
  private EstadoSolicitudAtencion estado;
  private Prorroga prorroga;
  private Respuesta respuesta;

  public SolicitudAtencion(
      UUID id,
      NumeroRadicado numeroRadicado,
      TipoSolicitud tipo,
      CorreoElectronico correo,
      UUID pedidoId,
      Instant recibidaEn,
      Instant radicadaEn,
      String radicadaPor,
      String asunto,
      EstadoSolicitudAtencion estado,
      Prorroga prorroga,
      Respuesta respuesta) {
    this.id = Objects.requireNonNull(id, "El id de la solicitud no puede ser nulo.");
    this.numeroRadicado =
        Objects.requireNonNull(numeroRadicado, "El número de radicado no puede ser nulo.");
    this.tipo = Objects.requireNonNull(tipo, "El tipo de solicitud no puede ser nulo.");
    this.correo = Objects.requireNonNull(correo, "El correo del interesado no puede ser nulo.");
    this.pedidoId = pedidoId;
    this.recibidaEn =
        Objects.requireNonNull(recibidaEn, "La fecha en que llegó la solicitud no puede ser nula.");
    this.radicadaEn =
        Objects.requireNonNull(radicadaEn, "La fecha de radicación no puede ser nula.");
    if (recibidaEn.isAfter(radicadaEn)) {
      throw new ExcepcionDeDominio("Una solicitud no se puede radicar antes de haber llegado.");
    }
    if (radicadaPor == null || radicadaPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien radica una solicitud no puede quedar en blanco.");
    }
    this.radicadaPor = radicadaPor;
    if (asunto == null || asunto.isBlank()) {
      throw new ExcepcionDeDominio("Una solicitud sin asunto no se puede atender.");
    }
    this.asunto = asunto;
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    if (prorroga == null && estado == EstadoSolicitudAtencion.PRORROGADA) {
      throw new ExcepcionDeDominio("Una solicitud prorrogada necesita su prórroga.");
    }
    this.prorroga = prorroga;
    if (respuesta == null && estado == EstadoSolicitudAtencion.RESPONDIDA) {
      throw new ExcepcionDeDominio("Una solicitud respondida necesita su respuesta.");
    }
    this.respuesta = respuesta;
  }

  public static SolicitudAtencion radicar(
      NumeroRadicado numeroRadicado,
      TipoSolicitud tipo,
      CorreoElectronico correo,
      UUID pedidoId,
      Instant recibidaEn,
      Instant ahora,
      String radicadaPor,
      String asunto) {
    return new SolicitudAtencion(
        GeneradorIdentificador.nuevo(),
        numeroRadicado,
        tipo,
        correo,
        pedidoId,
        recibidaEn,
        ahora,
        radicadaPor,
        asunto,
        EstadoSolicitudAtencion.RADICADA,
        null,
        null);
  }

  public UUID id() {
    return id;
  }

  public NumeroRadicado numeroRadicado() {
    return numeroRadicado;
  }

  public TipoSolicitud tipo() {
    return tipo;
  }

  public CorreoElectronico correo() {
    return correo;
  }

  public Optional<UUID> pedidoId() {
    return Optional.ofNullable(pedidoId);
  }

  public Instant recibidaEn() {
    return recibidaEn;
  }

  public Instant radicadaEn() {
    return radicadaEn;
  }

  public String radicadaPor() {
    return radicadaPor;
  }

  public String asunto() {
    return asunto;
  }

  public EstadoSolicitudAtencion estado() {
    return estado;
  }

  public Optional<Prorroga> prorroga() {
    return Optional.ofNullable(prorroga);
  }

  public Optional<Respuesta> respuesta() {
    return Optional.ofNullable(respuesta);
  }

  /**
   * La prórroga solo vale si se avisa antes de que venza el plazo inicial, así que el agregado lo
   * comprueba en vez de confiar en que quien la otorga mire el calendario. Y solo la admiten los
   * tipos cuyo plazo la contempla: prorrogar donde el texto publicado no lo permite es incumplir el
   * propio texto.
   */
  public void prorrogar(Prorroga prorroga, PlazosDeAtencion plazos, CalendarioHabil calendario) {
    Objects.requireNonNull(prorroga, "La prórroga no puede ser nula.");
    PlazosDeAtencion.PlazoHabil plazo = plazos.para(tipo);
    if (!plazo.admiteProrroga()) {
      throw new ExcepcionDeDominio(
          "Una solicitud de tipo " + tipo + " no admite prórroga del plazo de respuesta.");
    }
    if (prorroga.avisadaEn().isAfter(limiteInicial(plazos, calendario))) {
      throw new ExcepcionDeDominio(
          "El aviso de la prórroga llegó después de vencido el plazo inicial: no prorroga nada.");
    }
    transicionar(EstadoSolicitudAtencion.PRORROGADA);
    this.prorroga = prorroga;
  }

  public void responder(Respuesta respuesta) {
    Objects.requireNonNull(respuesta, "La respuesta no puede ser nula.");
    transicionar(EstadoSolicitudAtencion.RESPONDIDA);
    this.respuesta = respuesta;
  }

  private void transicionar(EstadoSolicitudAtencion siguiente) {
    if (!estado.puedeTransicionarA(siguiente)) {
      throw new ExcepcionDeDominio(
          "Una solicitud " + estado + " no puede pasar a " + siguiente + ".");
    }
    estado = siguiente;
  }

  private Instant limiteInicial(PlazosDeAtencion plazos, CalendarioHabil calendario) {
    return PlazoDeRespuesta.limite(recibidaEn, plazos.para(tipo).diasHabiles(), calendario);
  }

  /** El límite que de verdad corre hoy: el inicial, o el prorrogado si hubo prórroga válida. */
  public Instant limiteDeRespuesta(PlazosDeAtencion plazos, CalendarioHabil calendario) {
    PlazosDeAtencion.PlazoHabil plazo = plazos.para(tipo);
    int dias = plazo.diasHabiles() + (prorroga == null ? 0 : plazo.diasProrroga());
    return PlazoDeRespuesta.limite(recibidaEn, dias, calendario);
  }

  /**
   * Sin responder todavía, mide contra {@code ahora}: dice si aún se está a tiempo. Ya respondida,
   * mide contra la fecha de la respuesta y el veredicto deja de moverse — es el dato que hay que
   * poder mostrar después, y no puede empeorar solo porque pase el tiempo.
   */
  public VerdictoPlazo verdictoDeRespuesta(
      Instant ahora, PlazosDeAtencion plazos, CalendarioHabil calendario) {
    Instant referencia = respuesta == null ? ahora : respuesta.respondidaEn();
    return PlazoDeRespuesta.verdicto(limiteDeRespuesta(plazos, calendario), referencia, calendario);
  }
}
