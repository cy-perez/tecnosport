package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Los mensajes registrados, tal como llegaron. */
public interface RepositorioMensajesProveedor {

  /**
   * Guarda los mensajes <b>recordando el orden de la lista</b>, que es el de la fuente: con él se
   * desempatan los del mismo instante al leerlos.
   */
  void guardarTodos(List<MensajeProveedor> mensajes);

  /**
   * De estos identificadores, cuáles ya están registrados para el proveedor. Es la consulta que
   * hace inofensivo volver a subir la misma exportación: se pregunta por el lote entero de una vez,
   * no mensaje a mensaje.
   */
  Set<IdExternoDeMensaje> idsExternosExistentes(
      UUID proveedorId, Collection<IdExternoDeMensaje> candidatos);

  /**
   * Los de un lote, en orden de envío; los del mismo instante, en el orden en que se guardaron.
   *
   * <p>El desempate no es un detalle: una exportación de Android no trae segundos, y el agrupador
   * decide de qué precio es una foto por lo que tiene antes y después. Si el empate saliera en
   * cualquier orden, la misma exportación armaría publicaciones distintas en cada corrida.
   */
  List<MensajeProveedor> listarDeLote(UUID loteId);

  /**
   * Los de estos ids, de cualquier lote, en el orden en que se piden; los que ya no existen no
   * vienen. Para las fotos que un borrador sumó de otra publicación.
   */
  List<MensajeProveedor> buscarPorIds(List<UUID> ids);

  void eliminarTodos(Collection<UUID> ids);
}
