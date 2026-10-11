package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Un producto tal como el proveedor lo anunció: el texto con el precio, los textos que siguieron y
 * las fotos, en orden.
 *
 * <p>No guarda los mensajes sino sus identificadores: el mensaje es el material y la publicación es
 * la forma de leerlo. Rehacer la agrupación con otra ventana produce otras publicaciones sobre los
 * mismos mensajes, y por eso las publicaciones son de un lote y no de un proveedor.
 */
public final class PublicacionProveedor {

  private final UUID id;
  private final UUID proveedorId;
  private final UUID loteId;
  private final UUID mensajePrincipalId;
  private final List<UUID> textosAdicionales;
  private final List<UUID> medios;
  private final Instant fecha;
  private EstadoPublicacionProveedor estado;
  private CierreDePublicacion cierre;

  /**
   * Lo que pasó por dentro, para quien diagnostica: el mensaje de la API que rechazó la extracción,
   * la excepción. No se muestra en el panel —el panel traduce el {@link #cierre}—; está en la base
   * para que una ingesta como la de Meraki se explique con una consulta.
   */
  private String detalle;

  public PublicacionProveedor(
      UUID id,
      UUID proveedorId,
      UUID loteId,
      UUID mensajePrincipalId,
      List<UUID> textosAdicionales,
      List<UUID> medios,
      Instant fecha,
      EstadoPublicacionProveedor estado,
      CierreDePublicacion cierre,
      String detalle) {
    this.id = Objects.requireNonNull(id, "El id de la publicación no puede ser nulo.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Una publicación es de un proveedor.");
    this.loteId = Objects.requireNonNull(loteId, "Una publicación nace en un lote.");
    this.mensajePrincipalId =
        Objects.requireNonNull(mensajePrincipalId, "Una publicación tiene un mensaje principal.");
    this.textosAdicionales =
        new ArrayList<>(Objects.requireNonNullElse(textosAdicionales, List.of()));
    this.medios = new ArrayList<>(Objects.requireNonNullElse(medios, List.of()));
    this.fecha = Objects.requireNonNull(fecha, "Una publicación tiene fecha.");
    this.estado = Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    this.cierre = cierre;
    this.detalle = enBlancoEsNulo(detalle);
    if ((estado == EstadoPublicacionProveedor.DESCARTADA
            || estado == EstadoPublicacionProveedor.ERROR)
        && this.cierre == null) {
      throw new ExcepcionDeDominio("Una publicación " + estado + " tiene que decir por qué.");
    }
  }

  public static PublicacionProveedor abrir(MensajeProveedor principal) {
    Objects.requireNonNull(principal, "Una publicación se abre con un mensaje.");
    PublicacionProveedor publicacion =
        new PublicacionProveedor(
            GeneradorIdentificador.nuevo(),
            principal.proveedorId(),
            principal.loteId(),
            principal.id(),
            List.of(),
            List.of(),
            principal.enviadoEn(),
            EstadoPublicacionProveedor.PENDIENTE_EXTRACCION,
            null,
            null);
    if (principal.tipo() == TipoMensaje.IMAGEN) {
      publicacion.medios.add(principal.id());
    }
    return publicacion;
  }

  public void anexar(MensajeProveedor mensaje) {
    exigirPendiente("anexar mensajes a");
    switch (mensaje.tipo()) {
      case TEXTO -> textosAdicionales.add(mensaje.id());
      case IMAGEN -> medios.add(mensaje.id());
      case OTRO -> {
        // Un audio o un documento no aporta nada a la ficha; no se anexa.
      }
    }
  }

  public void marcarExtraida() {
    exigirPendiente("extraer");
    this.estado = EstadoPublicacionProveedor.EXTRAIDA;
  }

  /** No era un producto, o ya hay un borrador que lo tiene: un saludo, un anuncio repetido. */
  public void descartar(CierreDePublicacion cierre) {
    exigirPendiente("descartar");
    this.cierre = Objects.requireNonNull(cierre, "Hace falta el motivo.");
    this.estado = EstadoPublicacionProveedor.DESCARTADA;
  }

  /**
   * No se pudo leer.
   *
   * @param detalle lo que pasó por dentro, para quien diagnostica; nulo si no hay
   */
  public void fallar(CierreDePublicacion cierre, String detalle) {
    exigirPendiente("fallar");
    this.cierre = Objects.requireNonNull(cierre, "Hace falta el motivo.");
    this.detalle = enBlancoEsNulo(detalle);
    this.estado = EstadoPublicacionProveedor.ERROR;
  }

  /**
   * Una publicación en {@code ERROR} vuelve a esperar al extractor: alguien pidió reintentar las
   * fallidas del lote. Las descartadas no: esas se leyeron y no eran productos.
   */
  public void reabrir() {
    if (estado != EstadoPublicacionProveedor.ERROR) {
      throw new ExcepcionDeDominio("Solo se reabre una publicación en error; esta está " + estado);
    }
    this.estado = EstadoPublicacionProveedor.PENDIENTE_EXTRACCION;
    this.cierre = null;
    this.detalle = null;
  }

  private void exigirPendiente(String accion) {
    if (estado != EstadoPublicacionProveedor.PENDIENTE_EXTRACCION) {
      throw new ExcepcionDeDominio(
          "Solo se puede " + accion + " una publicación pendiente; esta está " + estado + ".");
    }
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public UUID loteId() {
    return loteId;
  }

  public UUID mensajePrincipalId() {
    return mensajePrincipalId;
  }

  public List<UUID> textosAdicionales() {
    return List.copyOf(textosAdicionales);
  }

  public List<UUID> medios() {
    return List.copyOf(medios);
  }

  public Instant fecha() {
    return fecha;
  }

  public EstadoPublicacionProveedor estado() {
    return estado;
  }

  public Optional<CierreDePublicacion> cierre() {
    return Optional.ofNullable(cierre);
  }

  public Optional<String> detalle() {
    return Optional.ofNullable(detalle);
  }
}
