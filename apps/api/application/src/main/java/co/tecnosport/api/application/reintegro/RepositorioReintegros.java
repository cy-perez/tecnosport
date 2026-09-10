package co.tecnosport.api.application.reintegro;

import co.tecnosport.api.domain.reintegro.Reintegro;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de la constancia del dinero devuelto, compartido por los cinco caminos que la producen.
 *
 * <p>{@code buscarPorPedido} existe para la pantalla del pedido —qué se le devolvió y por qué— y
 * {@code buscarPorOrigen} para cerrar el círculo desde la solicitud que lo justificó.
 */
public interface RepositorioReintegros {

  void guardar(Reintegro reintegro);

  Optional<Reintegro> buscarPorId(UUID id);

  List<Reintegro> buscarPorPedido(UUID pedidoId);

  Optional<Reintegro> buscarPorOrigen(UUID origenId);
}
