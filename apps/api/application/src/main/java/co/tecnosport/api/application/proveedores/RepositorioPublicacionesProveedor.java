package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las publicaciones armadas a partir de los mensajes de un lote. */
public interface RepositorioPublicacionesProveedor {

  void guardarTodas(List<PublicacionProveedor> publicaciones);

  void actualizar(PublicacionProveedor publicacion);

  Optional<PublicacionProveedor> buscarPorId(UUID id);

  /** Las de un lote, en orden de fecha. */
  List<PublicacionProveedor> listarDeLote(UUID loteId);
}
