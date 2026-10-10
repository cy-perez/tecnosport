package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Las publicaciones armadas a partir de los mensajes de un lote. */
public interface RepositorioPublicacionesProveedor {

  void guardarTodas(List<PublicacionProveedor> publicaciones);

  void actualizar(PublicacionProveedor publicacion);

  Optional<PublicacionProveedor> buscarPorId(UUID id);

  /** Las de un lote, en orden de fecha. */
  List<PublicacionProveedor> listarDeLote(UUID loteId);

  /** Con su composición. Los mensajes no: esos se borran aparte, y solo si nadie más los usa. */
  void eliminar(UUID id);

  /**
   * El texto de cada anuncio que llegó en el chat de caballero del proveedor —el del mensaje
   * principal de cada publicación de esos lotes—, para descartar lo que el chat general repite.
   */
  List<String> textosDelChatDeCaballero(UUID proveedorId);

  /**
   * De estos mensajes, los que alguna otra publicación usa, como principal o en su composición.
   * Rehacer la agrupación de un lote arma publicaciones nuevas sobre los mismos mensajes.
   */
  Set<UUID> mensajesUsadosPorOtras(UUID publicacionId, Collection<UUID> mensajeIds);
}
