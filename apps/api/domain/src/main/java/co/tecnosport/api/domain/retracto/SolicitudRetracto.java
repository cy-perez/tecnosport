package co.tecnosport.api.domain.retracto;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
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
 * <p>{@code verdictoAlRadicar} se congela al crear y no se recalcula después. Es una foto de lo que
 * se sabía ese día: el calendario de festivos puede cargarse más adelante y cambiaría un veredicto
 * ya usado para tomar una decisión, que es justo lo que no debe pasar.
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

  public SolicitudRetracto(
      UUID id,
      UUID pedidoId,
      Instant radicadaEn,
      String radicadaPor,
      String motivo,
      VerdictoPlazo verdictoAlRadicar,
      EstadoSolicitudRetracto estado,
      Instant productoRecibidoEn) {
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
  }

  /**
   * El motivo es opcional y así tiene que ser: el retracto se ejerce "sin necesidad de justificar
   * la decisión" (art. 47). Exigirlo aquí convertiría un derecho incondicional en un trámite con
   * condiciones.
   */
  public static SolicitudRetracto radicar(
      UUID pedidoId,
      Instant entregadoEn,
      Instant ahora,
      String radicadaPor,
      String motivo,
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
        null);
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
