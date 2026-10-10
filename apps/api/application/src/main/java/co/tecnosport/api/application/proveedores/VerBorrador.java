package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El borrador con lo que hace falta para revisarlo: el texto original del proveedor y sus fotos,
 * cada una con una URL firmada de lectura porque el bucket es privado. Las que quien revisa
 * descartó no vienen. Las que se subieron desde el panel van después de las del proveedor.
 */
public final class VerBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final AlmacenDeArchivosDeProveedor almacen;

  public VerBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.almacen = Objects.requireNonNull(almacen);
  }

  public DetalleDeBorrador ejecutar(UUID borradorId) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(borrador.publicacionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    Map<UUID, MensajeProveedor> mensajes =
        repositorioMensajes.listarDeLote(publicacion.loteId()).stream()
            .collect(Collectors.toMap(MensajeProveedor::id, Function.identity()));

    List<String> textos = new ArrayList<>();
    MensajeProveedor principal = mensajes.get(publicacion.mensajePrincipalId());
    if (principal != null) {
      principal.textoLegible().ifPresent(textos::add);
    }
    for (UUID id : publicacion.textosAdicionales()) {
      MensajeProveedor mensaje = mensajes.get(id);
      if (mensaje != null) {
        mensaje.textoLegible().ifPresent(textos::add);
      }
    }
    List<FotoDeBorrador> fotos = new ArrayList<>();
    for (UUID id : publicacion.medios()) {
      MensajeProveedor mensaje = mensajes.get(id);
      if (mensaje == null || borrador.fotosDescartadas().contains(id)) {
        continue;
      }
      fotos.add(
          new FotoDeBorrador(
              mensaje.id(),
              mensaje.referenciaArchivo().map(r -> almacen.urlDeLectura(r).url()).orElse(null),
              mensaje.pieDeFoto().orElse(null),
              OrigenDeFoto.PROVEEDOR,
              borrador.tonosSugeridos().get(id)));
    }
    // Las de otras publicaciones que se le sumaron: la misma referencia publicada otra vez, o una
    // foto que quien revisa movió aquí. Las que ya no existen —se borró su lote— no se pintan.
    for (MensajeProveedor agregada : repositorioMensajes.buscarPorIds(borrador.fotosAgregadas())) {
      fotos.add(
          new FotoDeBorrador(
              agregada.id(),
              agregada.referenciaArchivo().map(r -> almacen.urlDeLectura(r).url()).orElse(null),
              agregada.pieDeFoto().orElse(null),
              OrigenDeFoto.PROVEEDOR,
              borrador.tonosSugeridos().get(agregada.id())));
    }
    for (FotoSubida subida : borrador.fotosSubidas()) {
      fotos.add(
          new FotoDeBorrador(
              subida.id(),
              almacen.urlDeLectura(subida.referenciaArchivo()).url(),
              null,
              OrigenDeFoto.PANEL));
    }
    List<Hermano> hermanos =
        repositorioBorradores.listarDePublicacion(publicacion.id()).stream()
            .filter(otro -> !otro.id().equals(borrador.id()))
            .filter(otro -> otro.estado() == EstadoBorrador.EN_REVISION)
            .map(otro -> new Hermano(otro.id(), otro.titulo().orElse(null)))
            .toList();
    return new DetalleDeBorrador(borrador, publicacion, textos, fotos, hermanos);
  }

  /**
   * @param hermanos los otros borradores en revisión de la misma publicación —el otro producto del
   *     conjunto, los otros diseños del álbum—: a donde el panel deja mover una foto
   */
  public record DetalleDeBorrador(
      BorradorProducto borrador,
      PublicacionProveedor publicacion,
      List<String> textos,
      List<FotoDeBorrador> fotos,
      List<Hermano> hermanos) {

    public DetalleDeBorrador(
        BorradorProducto borrador,
        PublicacionProveedor publicacion,
        List<String> textos,
        List<FotoDeBorrador> fotos) {
      this(borrador, publicacion, textos, fotos, List.of());
    }
  }

  /** Otro borrador de la misma publicación, como lo nombra el panel. */
  public record Hermano(UUID id, String titulo) {}

  /** De dónde salió la foto: del mensaje del proveedor o de quien revisa, desde el panel. */
  public enum OrigenDeFoto {
    PROVEEDOR,
    PANEL
  }

  /**
   * @param mensajeId el id de la foto: el del mensaje si la mandó el proveedor, el de la {@link
   *     FotoSubida} si se subió desde el panel. Se llama así por el contrato que ya usaba la
   *     aprobación; los dos son UUID generados aquí y no chocan.
   * @param url firmada y de vida corta; nula cuando la exportación omitió el archivo
   * @param tonoSugerido el color que la lectura de fotos vio en ella; nulo si no hay sugerencia
   */
  public record FotoDeBorrador(
      UUID mensajeId, String url, String pieDeFoto, OrigenDeFoto origen, String tonoSugerido) {

    public FotoDeBorrador(UUID mensajeId, String url, String pieDeFoto, OrigenDeFoto origen) {
      this(mensajeId, url, pieDeFoto, origen, null);
    }
  }
}
