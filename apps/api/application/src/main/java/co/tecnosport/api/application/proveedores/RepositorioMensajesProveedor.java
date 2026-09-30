package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Los mensajes registrados, tal como llegaron. */
public interface RepositorioMensajesProveedor {

  void guardarTodos(List<MensajeProveedor> mensajes);

  /**
   * De estos identificadores, cuáles ya están registrados para el proveedor. Es la consulta que
   * hace inofensivo volver a subir la misma exportación: se pregunta por el lote entero de una vez,
   * no mensaje a mensaje.
   */
  Set<IdExternoDeMensaje> idsExternosExistentes(
      UUID proveedorId, Collection<IdExternoDeMensaje> candidatos);

  /** Los de un lote, en orden de envío. */
  List<MensajeProveedor> listarDeLote(UUID loteId);
}
