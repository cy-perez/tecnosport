package co.tecnosport.api.domain.garantia;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una reclamación de garantía sobre una línea concreta de un pedido entregado.
 *
 * <p>Agregado propio y no campos de la {@code SolicitudAtencion} que la contiene, por la misma
 * razón que {@code Envio} o {@code SolicitudRetracto}: tiene datos que ninguna otra solicitud tiene
 * —qué variante falló, desde cuándo corre el término, cuál de las tres salidas se eligió— y
 * meterlos en el agregado genérico llenaría de nulos a las peticiones y las quejas.
 *
 * <p>El vínculo con la solicitud es {@code solicitudId}. Radicar una garantía radica también su
 * solicitud de atención, con su número y su plazo de respuesta: para el comprador es una sola cosa,
 * y separarlas dejaría reclamaciones sin reloj corriendo.
 *
 * <p><b>{@code mesesDeTermino} se congela al radicar y no se recalcula.</b> Es una foto de lo que
 * se sabía ese día, mismo criterio que {@code verdictoAlRadicar} en el retracto: el término de una
 * categoría puede cargarse o cambiar más adelante, y mover hacia atrás una vigencia ya usada para
 * decidir es justo lo que no debe pasar. Vacío significa que ese día nadie sabía el término — hoy
 * es el caso de los celulares.
 */
public final class ReclamacionGarantia {

  private final UUID id;
  private final UUID solicitudId;
  private final UUID pedidoId;
  private final UUID varianteId;
  private final Instant entregadoEn;
  private final Instant radicadaEn;
  private final Integer mesesDeTermino;
  private final String descripcionDelFallo;
  private EstadoReclamacionGarantia estado;
  private DesenlaceGarantia desenlace;
  private Instant resueltaEn;
  private String resueltaPor;
  private UUID reintegroId;

  public ReclamacionGarantia(
      UUID id,
      UUID solicitudId,
      UUID pedidoId,
      UUID varianteId,
      Instant entregadoEn,
      Instant radicadaEn,
      Integer mesesDeTermino,
      String descripcionDelFallo,
      EstadoReclamacionGarantia estado,
      DesenlaceGarantia desenlace,
      Instant resueltaEn,
      String resueltaPor,
      UUID reintegroId) {
    this.id = Objects.requireNonNull(id, "El id de la reclamación no puede ser nulo.");
    this.solicitudId =
        Objects.requireNonNull(solicitudId, "La reclamación necesita su solicitud de atención.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    this.varianteId =
        Objects.requireNonNull(varianteId, "Una garantía se reclama sobre un producto concreto.");
    this.entregadoEn =
        Objects.requireNonNull(entregadoEn, "El término de garantía corre desde la entrega.");
    this.radicadaEn =
        Objects.requireNonNull(radicadaEn, "La fecha de radicación no puede ser nula.");
    if (mesesDeTermino != null && mesesDeTermino <= 0) {
      throw new ExcepcionDeDominio("Un término de garantía de cero meses no es un término.");
    }
    this.mesesDeTermino = mesesDeTermino;
    if (descripcionDelFallo == null || descripcionDelFallo.isBlank()) {
      throw new ExcepcionDeDominio(
          "Una reclamación de garantía sin descripción del fallo no se puede atender.");
    }
    this.descripcionDelFallo = descripcionDelFallo;
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    if (estado == EstadoReclamacionGarantia.RESUELTA
        && (desenlace == null || resueltaEn == null || resueltaPor == null)) {
      throw new ExcepcionDeDominio(
          "Una reclamación resuelta necesita su desenlace, su fecha y quién la resolvió.");
    }
    if (desenlace == DesenlaceGarantia.REINTEGRO
        && estado == EstadoReclamacionGarantia.RESUELTA
        && reintegroId == null) {
      throw new ExcepcionDeDominio(
          "Una garantía resuelta con reintegro necesita el id de su constancia.");
    }
    this.desenlace = desenlace;
    this.resueltaEn = resueltaEn;
    this.resueltaPor = resueltaPor;
    this.reintegroId = reintegroId;
  }

  public static ReclamacionGarantia radicar(
      UUID solicitudId,
      UUID pedidoId,
      UUID varianteId,
      Instant entregadoEn,
      Instant ahora,
      Integer mesesDeTermino,
      String descripcionDelFallo) {
    return new ReclamacionGarantia(
        GeneradorIdentificador.nuevo(),
        solicitudId,
        pedidoId,
        varianteId,
        entregadoEn,
        ahora,
        mesesDeTermino,
        descripcionDelFallo,
        EstadoReclamacionGarantia.RADICADA,
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

  public UUID varianteId() {
    return varianteId;
  }

  public Instant entregadoEn() {
    return entregadoEn;
  }

  public Instant radicadaEn() {
    return radicadaEn;
  }

  public Optional<Integer> mesesDeTermino() {
    return Optional.ofNullable(mesesDeTermino);
  }

  public String descripcionDelFallo() {
    return descripcionDelFallo;
  }

  public EstadoReclamacionGarantia estado() {
    return estado;
  }

  public Optional<DesenlaceGarantia> desenlace() {
    return Optional.ofNullable(desenlace);
  }

  public Optional<Instant> resueltaEn() {
    return Optional.ofNullable(resueltaEn);
  }

  public Optional<String> resueltaPor() {
    return Optional.ofNullable(resueltaPor);
  }

  public Optional<UUID> reintegroId() {
    return Optional.ofNullable(reintegroId);
  }

  /**
   * El último instante amparado: la fecha de entrega más el término, al final del día. Vacío cuando
   * el término de esa categoría no se conocía al radicar.
   */
  public Optional<Instant> finDelTermino() {
    if (mesesDeTermino == null) {
      return Optional.empty();
    }
    LocalDate fin =
        entregadoEn.atZone(ZonaDelNegocio.ZONA).toLocalDate().plusMonths(mesesDeTermino);
    return Optional.of(fin.plusDays(1).atStartOfDay(ZonaDelNegocio.ZONA).toInstant());
  }

  /**
   * Se mide contra la fecha de radicación y no contra "ahora": lo que importa es si el producto
   * estaba amparado cuando el comprador reclamó, no si lo sigue estando mientras alguien mira la
   * pantalla. Una reclamación presentada a tiempo no deja de estarlo porque el negocio tarde en
   * atenderla.
   */
  public VigenciaGarantia vigencia() {
    return finDelTermino()
        .map(
            fin ->
                radicadaEn.isAfter(fin)
                    ? VigenciaGarantia.FUERA_DE_TERMINO
                    : VigenciaGarantia.CUBIERTA)
        .orElse(VigenciaGarantia.INDETERMINADA);
  }

  /**
   * Resolver nunca se bloquea por la vigencia, ni siquiera fuera de término: puede haber garantía
   * del fabricante por detrás o una decisión comercial, y quien decide es una persona con la
   * vigencia delante. Lo que sí se exige es que el desenlace quede escrito.
   *
   * <p>{@code reintegroId} solo tiene sentido con {@code REINTEGRO}, y es obligatorio con él: una
   * garantía cerrada "devolviendo el dinero" sin constancia de que salió es exactamente lo que la
   * ley pide poder demostrar.
   */
  public void resolver(DesenlaceGarantia desenlace, UUID reintegroId, Instant ahora, String actor) {
    Objects.requireNonNull(desenlace, "El desenlace no puede ser nulo.");
    Objects.requireNonNull(ahora, "La fecha de resolución no puede ser nula.");
    if (actor == null || actor.isBlank()) {
      throw new ExcepcionDeDominio("Quien resuelve una garantía no puede quedar en blanco.");
    }
    if (estado == EstadoReclamacionGarantia.RESUELTA) {
      throw new ExcepcionDeDominio("Esta reclamación de garantía ya se resolvió.");
    }
    if (desenlace == DesenlaceGarantia.REINTEGRO && reintegroId == null) {
      throw new ExcepcionDeDominio(
          "Una garantía resuelta con reintegro necesita el id de su constancia.");
    }
    if (desenlace != DesenlaceGarantia.REINTEGRO && reintegroId != null) {
      throw new ExcepcionDeDominio(
          "Solo un desenlace de reintegro puede apuntar a una constancia de dinero devuelto.");
    }
    this.desenlace = desenlace;
    this.reintegroId = reintegroId;
    this.resueltaEn = ahora;
    this.resueltaPor = actor;
    this.estado = EstadoReclamacionGarantia.RESUELTA;
  }
}
