package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * Un producto tiene a lo sumo un set de rotación publicado: la ficha muestra uno solo, y con dos
 * publicados cuál se ve sería cuestión de suerte. Para reemplazarlo hay que borrar el anterior
 * primero — no hay reemplazo en caliente, y por eso el producto se queda unos segundos sin visor.
 */
public final class SetRotacionPublicadoExistenteException extends RuntimeException {

  public SetRotacionPublicadoExistenteException(UUID productoId, UUID setPublicadoId) {
    super(
        "El producto '"
            + productoId
            + "' ya tiene el set de rotación '"
            + setPublicadoId
            + "' publicado; hay que borrarlo antes de publicar otro.");
  }
}
