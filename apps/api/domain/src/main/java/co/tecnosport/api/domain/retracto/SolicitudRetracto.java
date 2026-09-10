package co.tecnosport.api.domain.retracto;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una solicitud de retracto sobre un pedido entregado (Ley 1480 de 2011, art. 47).
 *
 * <p>Agregado propio y no un campo del pedido, por la misma razón que {@code Envio}
 * (docs/adr/0013): tiene su propio ciclo de vida, su propia fecha y su propio desenlace, y puede
 * terminar en nada sin que el pedido cambie. El vínculo es {@code pedidoId}, sin objeto.
 *
 * <p>El canal es el que los términos publicados prometen —correo o WhatsApp— así que quien radica
 * es siempre una persona del negocio, no el comprador: de ahí {@code radicadaPor}. Este agregado
 * deja constancia de un acto que ocurre por fuera; no lo sustituye.
 *
 * <p>{@code verdictoAlRadicar} se congela al crear y no se recalcula después, y la razón cambió sin
 * que el campo cambiara. Nació porque el calendario de festivos era un dato pendiente y cargarlo
 * habría movido un veredicto ya usado para decidir; desde {@code ADR-0024} los festivos se calculan
 * y ese riesgo desapareció. Sigue congelado por otro motivo, más duradero: la <b>Ley 2578 de
 * 2026</b> declaró festivo el 9 de julio con el año ya empezado, y contra ella hay una demanda de
 * constitucionalidad en curso. Un calendario legal cambia, y con él cambiaría hacia atrás el
 * veredicto con el que alguien decidió algo. La foto del día en que se radicó es lo que se puede
 * defender después.
 */
public final class SolicitudRetracto {

  private final UUID id;
  private final UUID pedidoId;
  private final Instant radicadaEn;
  private final String radicadaPor;
  private final String motivo;
  private final VerdictoPlazo verdictoAlRadicar;
  private EstadoSolicitudRetracto estado;
  private Instant productoRecibidoEn;
  private UUID reintegroId;
  private MedioReintegro medioPreferido;

  public SolicitudRetracto(
      UUID id,
      UUID pedidoId,
      Instant radicadaEn,
      String radicadaPor,
      String motivo,
      VerdictoPlazo verdictoAlRadicar,
      EstadoSolicitudRetracto estado,
      Instant productoRecibidoEn,
      UUID reintegroId,
      MedioReintegro medioPreferido) {
    this.id = Objects.requireNonNull(id, "El id de la solicitud no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    this.radicadaEn =
        Objects.requireNonNull(radicadaEn, "La fecha de radicación no puede ser nula.");
    if (radicadaPor == null || radicadaPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien radica un retracto no puede quedar en blanco.");
    }
    this.radicadaPor = radicadaPor;
    this.motivo = motivo == null || motivo.isBlank() ? null : motivo;
    this.verdictoAlRadicar =
        Objects.requireNonNull(verdictoAlRadicar, "El veredicto de plazo no puede ser nulo.");
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    if (productoRecibidoEn == null && estado == EstadoSolicitudRetracto.PRODUCTO_RECIBIDO) {
      throw new ExcepcionDeDominio(
          "Una solicitud con el producto recibido necesita la fecha en que volvió.");
    }
    this.productoRecibidoEn = productoRecibidoEn;
    if (reintegroId == null && estado == EstadoSolicitudRetracto.REEMBOLSADA) {
      throw new ExcepcionDeDominio(
          "Una solicitud reembolsada necesita el id de su constancia de reintegro.");
    }
    this.reintegroId = reintegroId;
    this.medioPreferido = medioPreferido;
  }

  /**
   * El motivo es opcional y así tiene que ser: el retracto se ejerce "sin necesidad de justificar
   * la decisión" (art. 47). Exigirlo aquí convertiría un derecho incondicional en un trámite con
   * condiciones.
   *
   * <p>{@code medioPreferido} también es opcional, y por otra razón: el comprador puede decirlo en
   * el mismo correo con que se retracta o mandarlo después, y no se le puede exigir para arrancar
   * el trámite. Ver {@link #anotarMedioPreferido}.
   */
  public static SolicitudRetracto radicar(
      UUID pedidoId,
      Instant entregadoEn,
      Instant ahora,
      String radicadaPor,
      String motivo,
      MedioReintegro medioPreferido,
      CalendarioHabil calendario) {
    Objects.requireNonNull(entregadoEn, "Un pedido sin entregar no admite retracto.");
    return new SolicitudRetracto(
        UUID.randomUUID(),
        pedidoId,
        ahora,
        radicadaPor,
        motivo,
        PlazoDeRetracto.verdicto(entregadoEn, ahora, calendario),
        EstadoSolicitudRetracto.RADICADA,
        null,
        null,
        medioPreferido);
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public Instant radicadaEn() {
    return radicadaEn;
  }

  public String radicadaPor() {
    return radicadaPor;
  }

  public Optional<String> motivo() {
    return Optional.ofNullable(motivo);
  }

  public VerdictoPlazo verdictoAlRadicar() {
    return verdictoAlRadicar;
  }

  public EstadoSolicitudRetracto estado() {
    return estado;
  }

  public Optional<Instant> productoRecibidoEn() {
    return Optional.ofNullable(productoRecibidoEn);
  }

  /**
   * El producto volvió. Es su propio método y no un {@code transicionar} más porque este paso trae
   * un dato consigo: la fecha desde la que corre el plazo de reintegro contra el negocio.
   */
  public void recibirProducto(Instant ahora) {
    Objects.requireNonNull(ahora, "La fecha en que vuelve el producto no puede ser nula.");
    transicionar(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO);
    this.productoRecibidoEn = ahora;
  }

  public Optional<UUID> reintegroId() {
    return Optional.ofNullable(reintegroId);
  }

  /**
   * Apunta a la constancia del dinero devuelto y cierra la solicitud. Como {@link
   * #recibirProducto}, es su propio método porque el paso trae un dato: sin la constancia no hay
   * forma de demostrar que se cumplió el plazo del artículo 47, y demostrarlo es justo para lo que
   * existe esto.
   *
   * <p>Guarda el id y no el {@code Reintegro} entero porque son dos agregados: la constancia es una
   * sola para los cinco caminos que devuelven dinero, y el retracto es uno de ellos. Exigirlo aquí
   * es lo que impide que una solicitud se declare reembolsada sin que exista nada detrás — la
   * invariante que se perdió al sacar la constancia de dentro, recuperada por el otro extremo.
   */
  public void registrarReintegro(UUID reintegroId) {
    Objects.requireNonNull(reintegroId, "El id del reintegro no puede ser nulo.");
    transicionar(EstadoSolicitudRetracto.REEMBOLSADA);
    this.reintegroId = reintegroId;
  }

  public Optional<MedioReintegro> medioPreferido() {
    return Optional.ofNullable(medioPreferido);
  }

  /**
   * Por dónde pidió el comprador que le devolvieran el dinero.
   *
   * <p>Existe porque la <b>Ley 2439 de 2024</b> no dejó la elección al negocio: la devolución
   * "deberá realizarse a través del medio de pago que prefiera el consumidor". Sin este dato no se
   * puede demostrar que se respetó, y quien tiene la carga de probar que cumplió es el negocio.
   *
   * <p>Se anota una vez y no se corrige. Cambiarla borraría la constancia de lo que el comprador
   * pidió, que es justo lo que este campo existe para conservar — si de verdad pidió otra cosa
   * después, eso es un hecho nuevo y va en el motivo o en la solicitud de atención, no encima del
   * anterior. Y después de devolver el dinero ya no se anota nada: anotar la preferencia con el
   * pago hecho es escribir el examen viendo las respuestas.
   */
  public void anotarMedioPreferido(MedioReintegro medio) {
    Objects.requireNonNull(medio, "El medio preferido no puede ser nulo.");
    if (estado == EstadoSolicitudRetracto.REEMBOLSADA) {
      throw new ExcepcionDeDominio(
          "El dinero ya se devolvió: anotar ahora lo que el comprador prefería no prueba nada.");
    }
    if (medioPreferido != null && medioPreferido != medio) {
      throw new ExcepcionDeDominio(
          "El comprador ya pidió "
              + medioPreferido
              + ": cambiarlo borraría la constancia de lo que pidió.");
    }
    this.medioPreferido = medio;
  }

  /**
   * Si devolver el dinero por {@code usado} respeta lo que el comprador pidió.
   *
   * <p>Sin preferencia anotada responde {@code true}: no se puede incumplir una preferencia que
   * nadie expresó. No bloquea nada —quien decide es una persona, y puede haber un motivo real, como
   * una cuenta que rebota— pero deja el contraste hecho para que el panel lo advierta antes de
   * guardar y para que después se pueda leer qué pasó.
   */
  public boolean respetaLaPreferencia(MedioReintegro usado) {
    Objects.requireNonNull(usado, "El medio usado no puede ser nulo.");
    return medioPreferido == null || medioPreferido == usado;
  }

  public void transicionar(EstadoSolicitudRetracto siguiente) {
    Objects.requireNonNull(siguiente, "El estado siguiente no puede ser nulo.");
    if (!estado.puedeTransicionarA(siguiente)) {
      throw new ExcepcionDeDominio(
          "Una solicitud " + estado + " no puede pasar a " + siguiente + ".");
    }
    estado = siguiente;
  }

  /**
   * El instante en que se agota el plazo de reintegro: quince días calendario —no hábiles— desde
   * que el comprador ejerce el derecho y cumple lo suyo, que es devolver el producto (art. 47,
   * modificado por la Ley 2439 de 2024). Vacío mientras el producto no haya vuelto: antes de eso no
   * hay plazo corriendo contra el negocio.
   */
  public Optional<Instant> limiteDeReintegro() {
    if (productoRecibidoEn == null) {
      return Optional.empty();
    }
    return Optional.of(
        productoRecibidoEn
            .atZone(PlazoDeRetracto.ZONA)
            .toLocalDate()
            .plusDays(15)
            .plusDays(1)
            .atStartOfDay(PlazoDeRetracto.ZONA)
            .toInstant());
  }
}
