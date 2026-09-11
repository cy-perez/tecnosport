package co.tecnosport.api.domain.reversion;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.Dinero;
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
 * <p>{@code fechaDeNoticia} es cuándo el comprador tuvo noticia de lo ocurrido, no cuándo compró ni
 * cuándo escribió. De ella cuelga el plazo que los términos publicados le imponen a él —cinco días
 * hábiles para solicitarla— y por eso se guarda aparte de {@code radicadaEn}.
 *
 * <p>Se llamaba {@code fechaDelHecho} hasta {@code V30}, y el nombre decía otra cosa que este mismo
 * javadoc: el panel pedía "Fecha del hecho" en la etiqueta y "cuándo el comprador tuvo noticia" en
 * la ayuda de abajo. Dos operadores leyendo lo mismo escribían dos fechas distintas en la misma
 * columna, y de un fraude uno se entera después: con la fecha del hecho, el panel podía marcar
 * vencida una solicitud que estaba en plazo. Lo levantó una revisión adversarial que leyó el nombre
 * y no la ayuda —exactamente lo que le pasa a quien llena el formulario—.
 *
 * <p><b>El decreto no usa un solo arranque, y el sitio sí.</b> El Decreto 587 de 2016, que
 * reglamenta el art. 51, cuenta los cinco días hábiles desde que el consumidor tuvo noticia de la
 * operación fraudulenta o no solicitada, <b>o de que el producto debió recibirse o se recibió
 * defectuoso</b>: son tres momentos según la causal. La cláusula publicada promete uno solo —la
 * noticia— y eso es deliberado y admisible, porque para las otras dos causales la noticia llega
 * igual o después que el momento del decreto: un defecto se descubre usando el producto, no al
 * recibirlo. O sea que la ventana que el sitio se autoimpone es <b>más amplia a favor del
 * consumidor</b> que la del decreto, y lo publicado obliga. Si algún día se quiere ceñir a la norma
 * causal por causal, este es el campo que se parte en tres y la cláusula que hay que cambiar con
 * él.
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
  private final Instant fechaDeNoticia;
  private final Instant radicadaEn;
  private final VerdictoPlazo verdictoAlRadicar;
  private EstadoSolicitudReversion estado;
  private Instant gestionadaEn;
  private String gestionadaPor;
  private String gestion;
  private DesenlaceReversion desenlace;
  private Instant resueltaEn;
  private UUID reintegroId;
  private Dinero montoRevertidoPorElEmisor;

  public SolicitudReversion(
      UUID id,
      UUID solicitudId,
      UUID pedidoId,
      CausalReversion causal,
      Instant fechaDeNoticia,
      Instant radicadaEn,
      VerdictoPlazo verdictoAlRadicar,
      EstadoSolicitudReversion estado,
      Instant gestionadaEn,
      String gestionadaPor,
      String gestion,
      DesenlaceReversion desenlace,
      Instant resueltaEn,
      UUID reintegroId,
      Dinero montoRevertidoPorElEmisor) {
    this.id = Objects.requireNonNull(id, "El id de la solicitud no puede ser nulo.");
    this.solicitudId =
        Objects.requireNonNull(solicitudId, "La reversión necesita su solicitud de atención.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    this.causal =
        Objects.requireNonNull(
            causal, "Una reversión sin causal no se puede tramitar: son tasadas.");
    this.fechaDeNoticia =
        Objects.requireNonNull(fechaDeNoticia, "La fecha del hecho no puede ser nula.");
    this.radicadaEn =
        Objects.requireNonNull(radicadaEn, "La fecha de radicación no puede ser nula.");
    if (fechaDeNoticia.isAfter(radicadaEn)) {
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
    this.montoRevertidoPorElEmisor = montoRevertidoPorElEmisor;
  }

  public static SolicitudReversion radicar(
      UUID solicitudId,
      UUID pedidoId,
      CausalReversion causal,
      Instant fechaDeNoticia,
      Instant ahora,
      CalendarioHabil calendario) {
    Objects.requireNonNull(calendario, "El calendario no puede ser nulo.");
    Objects.requireNonNull(fechaDeNoticia, "La fecha del hecho no puede ser nula.");
    return new SolicitudReversion(
        GeneradorIdentificador.nuevo(),
        solicitudId,
        pedidoId,
        causal,
        fechaDeNoticia,
        ahora,
        calendario.verdicto(
            calendario.limiteTrasDiasHabiles(fechaDeNoticia, DIAS_HABILES_PARA_SOLICITAR), ahora),
        EstadoSolicitudReversion.RADICADA,
        null,
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

  public Instant fechaDeNoticia() {
    return fechaDeNoticia;
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
  /**
   * {@code montoRevertidoPorElEmisor} solo se anota —y se exige— con {@code
   * REVERTIDO_POR_EL_EMISOR}, y es el hueco que cerró una revisión adversarial: ese desenlace no
   * deja {@code Reintegro} a propósito, porque el dinero volvió por la red de pagos y registrar un
   * pago que no hicimos descuadraría la constancia. Al no dejarlo, tampoco consumía el tope de lo
   * que un pedido puede devolver: un contracargo seguido de un retracto devolvía el total dos
   * veces.
   *
   * <p>La salida no es inventar la constancia, es <b>anotar el hecho</b>: cuánto revirtió el
   * emisor. Lo sabe quien resuelve, porque se lo dijo el emisor, y con eso {@code TopeDeReintegro}
   * puede contarlo sin fingir que salió de nuestra caja. Sirve además para el caso que antes no
   * tenía respuesta: una reversión parcial, que el artículo 51 y el Decreto 587 de 2016 contemplan
   * cuando la compra fue de varios productos.
   */
  public void resolver(
      DesenlaceReversion desenlace,
      UUID reintegroId,
      Dinero montoRevertidoPorElEmisor,
      Instant ahora) {
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
    boolean revirtioElEmisor = desenlace == DesenlaceReversion.REVERTIDO_POR_EL_EMISOR;
    if (revirtioElEmisor && montoRevertidoPorElEmisor == null) {
      throw new ExcepcionDeDominio(
          "Una reversión que hizo el emisor necesita cuánto revirtió: sin eso, ese dinero no cuenta"
              + " contra lo que el pedido todavía puede devolver.");
    }
    if (!revirtioElEmisor && montoRevertidoPorElEmisor != null) {
      throw new ExcepcionDeDominio(
          "Solo el desenlace en que revirtió el emisor lleva el monto que revirtió.");
    }
    this.montoRevertidoPorElEmisor = montoRevertidoPorElEmisor;
    this.desenlace = desenlace;
    this.reintegroId = reintegroId;
    this.resueltaEn = Objects.requireNonNull(ahora, "La fecha de resolución no puede ser nula.");
    this.estado = EstadoSolicitudReversion.RESUELTA;
  }

  /** Cuánto devolvió el emisor por su cuenta. Vacío en los otros tres desenlaces. */
  public Optional<Dinero> montoRevertidoPorElEmisor() {
    return Optional.ofNullable(montoRevertidoPorElEmisor);
  }
}
