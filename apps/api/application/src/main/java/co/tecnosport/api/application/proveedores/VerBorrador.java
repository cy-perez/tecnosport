package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
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
 * descartó no vienen.
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
              mensaje.pieDeFoto().orElse(null)));
    }
    return new DetalleDeBorrador(borrador, publicacion, textos, fotos);
  }

  public record DetalleDeBorrador(
      BorradorProducto borrador,
      PublicacionProveedor publicacion,
      List<String> textos,
      List<FotoDeBorrador> fotos) {}

  /**
   * @param url firmada y de vida corta; nula cuando la exportación omitió el archivo
   */
  public record FotoDeBorrador(UUID mensajeId, String url, String pieDeFoto) {}
}
