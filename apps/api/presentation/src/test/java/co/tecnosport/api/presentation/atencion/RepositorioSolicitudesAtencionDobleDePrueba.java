package co.tecnosport.api.presentation.atencion;

import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.NumeroRadicado;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioSolicitudesAtencionDobleDePrueba implements RepositorioSolicitudesAtencion {

  private final List<SolicitudAtencion> guardadas = new ArrayList<>();
  private long secuencial = 0;

  @Override
  public void guardar(SolicitudAtencion solicitud) {
    guardadas.removeIf(s -> s.id().equals(solicitud.id()));
    guardadas.add(solicitud);
  }

  @Override
  public Optional<SolicitudAtencion> buscarPorId(UUID id) {
    return guardadas.stream().filter(s -> s.id().equals(id)).findFirst();
  }

  @Override
  public Optional<SolicitudAtencion> buscarPorRadicado(NumeroRadicado numeroRadicado) {
    return guardadas.stream().filter(s -> s.numeroRadicado().equals(numeroRadicado)).findFirst();
  }

  @Override
  public List<SolicitudAtencion> buscarPorPedidoId(UUID pedidoId) {
    return guardadas.stream()
        .filter(s -> s.pedidoId().map(pedidoId::equals).orElse(false))
        .toList();
  }

  @Override
  public List<SolicitudAtencion> buscarPorEstado(EstadoSolicitudAtencion estado) {
    return guardadas.stream().filter(s -> s.estado() == estado).toList();
  }

  @Override
  public List<SolicitudAtencion> buscarAbiertas() {
    return guardadas.stream()
        .filter(s -> s.estado() != EstadoSolicitudAtencion.RESPONDIDA)
        .toList();
  }

  @Override
  public NumeroRadicado siguienteRadicado(int anio) {
    return NumeroRadicado.de(anio, ++secuencial);
  }
}
