package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Un puerto de reversiones que nunca tiene ninguna, para las pruebas de presentación que necesitan
 * armar {@code TopeDeReintegro} sin que las reversiones sean parte de lo que prueban.
 *
 * <p>Público y aquí, y no un doble por paquete: {@code presentation} no ve las clases de prueba de
 * {@code application} —son otro conjunto de fuentes y no hay {@code testFixtures}—, así que este es
 * el sitio donde puede compartirse. Se llama "vacío" y no "falso" a propósito: no guarda nada, y
 * cualquier prueba que necesite que guarde algo tiene que dejar de usarlo en vez de crecerlo.
 */
public final class RepositorioSolicitudesReversionVacio implements RepositorioSolicitudesReversion {

  @Override
  public void guardar(SolicitudReversion solicitud) {
    throw new UnsupportedOperationException(
        "Esta prueba no debería guardar reversiones: si hace falta, usa un doble que sí guarde.");
  }

  @Override
  public Optional<SolicitudReversion> buscarPorId(UUID id) {
    return Optional.empty();
  }

  @Override
  public Optional<SolicitudReversion> buscarPorSolicitudId(UUID solicitudId) {
    return Optional.empty();
  }

  @Override
  public List<SolicitudReversion> buscarPorPedidoId(UUID pedidoId) {
    return List.of();
  }
}
