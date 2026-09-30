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
  private String motivo;

  public PublicacionProveedor(
      UUID id,
      UUID proveedorId,
      UUID loteId,
      UUID mensajePrincipalId,
      List<UUID> textosAdicionales,
      List<UUID> medios,
      Instant fecha,
      EstadoPublicacionProveedor estado,
      String motivo) {
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
    this.motivo = enBlancoEsNulo(motivo);
    if ((estado == EstadoPublicacionProveedor.DESCARTADA
            || estado == EstadoPublicacionProveedor.ERROR)
        && this.motivo == null) {
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

  /** No era un producto: un saludo, una promoción, un aviso. */
  public void descartar(String motivo) {
    exigirPendiente("descartar");
    String porQue = exigirMotivo(motivo);
    this.estado = EstadoPublicacionProveedor.DESCARTADA;
    this.motivo = porQue;
  }

  public void fallar(String motivo) {
    exigirPendiente("fallar");
    String porQue = exigirMotivo(motivo);
    this.estado = EstadoPublicacionProveedor.ERROR;
    this.motivo = porQue;
  }

  private void exigirPendiente(String accion) {
    if (estado != EstadoPublicacionProveedor.PENDIENTE_EXTRACCION) {
      throw new ExcepcionDeDominio(
          "Solo se puede " + accion + " una publicación pendiente; esta está " + estado + ".");
    }
  }

  private static String exigirMotivo(String motivo) {
    String limpio = enBlancoEsNulo(motivo);
    if (limpio == null) {
      throw new ExcepcionDeDominio("Hace falta el motivo.");
    }
    return limpio;
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

  public Optional<String> motivo() {
    return Optional.ofNullable(motivo);
  }
}
