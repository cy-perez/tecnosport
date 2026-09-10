package co.tecnosport.api.application.reversion;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RadicarSolicitudComando;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.Objects;

/**
 * Radica una solicitud de reversión del pago con su causal.
 *
 * <p>No exige que el pedido haya pasado por nada: la reversión procede por fraude, por no entrega,
 * por producto distinto o por defectuoso, y ninguno de esos casos supone un retracto previo ni una
 * entrega. Un pedido puede llegar aquí sin haber pasado nunca por un retracto — y esa es justamente
 * la prueba de que las dos figuras no se confundieron en el modelo.
 *
 * <p>Como la garantía, radica también su solicitud de atención: sin ella la reversión no tendría
 * número que citar ni plazo de respuesta corriendo.
 */
public final class RadicarReversion {

  private final RepositorioSolicitudesReversion repositorioReversiones;
  private final RepositorioPedidos repositorioPedidos;
  private final RadicarSolicitud radicarSolicitud;
  private final CalendarioHabil calendario;
  private final Reloj reloj;

  public RadicarReversion(
      RepositorioSolicitudesReversion repositorioReversiones,
      RepositorioPedidos repositorioPedidos,
      RadicarSolicitud radicarSolicitud,
      CalendarioHabil calendario,
      Reloj reloj) {
    this.repositorioReversiones = Objects.requireNonNull(repositorioReversiones);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.radicarSolicitud = Objects.requireNonNull(radicarSolicitud);
    this.calendario = Objects.requireNonNull(calendario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudReversion ejecutar(RadicarReversionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));

    SolicitudAtencion solicitud =
        radicarSolicitud.ejecutar(
            new RadicarSolicitudComando(
                TipoSolicitud.REVERSION,
                pedido.correo().valor(),
                pedido.id(),
                comando.recibidaEn(),
                asunto(comando),
                comando.actor()));

    SolicitudReversion reversion =
        SolicitudReversion.radicar(
            solicitud.id(),
            pedido.id(),
            comando.causal(),
            comando.fechaDelHecho(),
            reloj.ahora(),
            calendario);
    repositorioReversiones.guardar(reversion);
    return reversion;
  }

  private static String asunto(RadicarReversionComando comando) {
    String descripcion = comando.descripcion() == null ? "" : comando.descripcion().trim();
    return descripcion.isEmpty()
        ? "Reversion del pago: " + comando.causal()
        : "Reversion del pago: " + descripcion;
  }
}
