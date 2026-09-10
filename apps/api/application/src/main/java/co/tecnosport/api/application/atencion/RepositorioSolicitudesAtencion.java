package co.tecnosport.api.application.atencion;

import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.NumeroRadicado;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code siguienteRadicado} lo resuelve la infraestructura y no el dominio, mismo motivo que {@code
 * RepositorioPedidos.siguienteNumero}: el secuencial exige atomicidad entre transacciones
 * concurrentes y eso solo lo da la base de datos.
 *
 * <p>{@code buscarAbiertas} devuelve lo que falta por responder, de lo más antiguo a lo más
 * reciente. El orden no es un detalle de presentación: un plazo legal que corre pone lo más viejo
 * arriba, mismo criterio que el recaudo pendiente.
 */
public interface RepositorioSolicitudesAtencion {

  void guardar(SolicitudAtencion solicitud);

  Optional<SolicitudAtencion> buscarPorId(UUID id);

  Optional<SolicitudAtencion> buscarPorRadicado(NumeroRadicado numeroRadicado);

  List<SolicitudAtencion> buscarPorPedidoId(UUID pedidoId);

  List<SolicitudAtencion> buscarPorEstado(EstadoSolicitudAtencion estado);

  List<SolicitudAtencion> buscarAbiertas();

  NumeroRadicado siguienteRadicado(int anio);
}
