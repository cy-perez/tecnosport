package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pedido.dto.EnvioPublicoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.GuiaPublicaRespuesta;
import co.tecnosport.api.presentation.pedido.dto.HistorialPedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoSeguimientoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.RetractoPublicoRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta del seguimiento público, que es la única que ve alguien sin sesión.
 *
 * <p>Mapeador aparte de {@link MapeadorRespuestasPedido} y no un método más suyo: aquel sirve al
 * panel, este al comprador, y mezclarlos fue justo lo que dejó salir el costo real del flete. Aquí
 * cada campo se escribe a mano desde el dominio, sin copiar una respuesta ya construida para otra
 * audiencia — copiar es lo que hace invisible una fuga.
 */
@Component
public class MapeadorSeguimiento {

  private final MapeadorRespuestasPedido mapeadorPedido;
  private final RepositorioEnvios repositorioEnvios;
  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioReintegros repositorioReintegros;

  public MapeadorSeguimiento(
      MapeadorRespuestasPedido mapeadorPedido,
      RepositorioEnvios repositorioEnvios,
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioReintegros repositorioReintegros) {
    this.mapeadorPedido = Objects.requireNonNull(mapeadorPedido);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
  }

  public PedidoSeguimientoRespuesta aRespuesta(Pedido pedido) {
    return aRespuesta(pedido, true);
  }

  /**
   * El mismo seguimiento <b>sin el {@code id} interno</b>, para el camino público que entra por el
   * número legible.
   *
   * <p>No es celo de más: ese UUID es la credencial de dos endpoints que sí escriben. {@code POST
   * /api/v1/pagos/intentos} pide <b>solo</b> el {@code pedidoId} —sin correo, y sin límite de
   * intentos— y crea una fila de pago firmando integridad contra la pasarela; {@code POST
   * /pedidos/&#123;id&#125;/reintentar-pago} re-reserva inventario con bloqueo pesimista. Todo el
   * diseño del límite de intentos se apoyaba en una frase que este endpoint dejó de hacer cierta:
   * "el id es un UUID v7, recorrerlo no es una opción". Recorrer el número sí lo es, y el número
   * devolvía el id.
   *
   * <p>Lo levantó la revisión de pagos, y lo que cierra es la escalada: quien adivine un número y
   * conozca el correo sigue viendo el pedido —ese es el riesgo asumido y documentado— pero ya no
   * puede tocarlo. El enlace del correo, que es donde el reintento tiene sentido, sí trae el id
   * porque quien lo abre ya lo tenía.
   */
  public PedidoSeguimientoRespuesta aRespuestaSinIdInterno(Pedido pedido) {
    return aRespuesta(pedido, false);
  }

  private PedidoSeguimientoRespuesta aRespuesta(Pedido pedido, boolean conIdInterno) {
    var completa = mapeadorPedido.aRespuesta(pedido);
    return new PedidoSeguimientoRespuesta(
        conIdInterno ? completa.id() : null,
        completa.numeroPedido(),
        completa.correo(),
        completa.contacto(),
        completa.lineas(),
        completa.tipoEntrega(),
        completa.direccion(),
        completa.metodoPago(),
        completa.estado(),
        // A mano desde el dominio, como todo lo demas de este mapeador: el desglose que el
        // articulo 50 exige antes de pagar tiene que seguir siendo legible despues de comprar, y
        // hasta ahora la pantalla de estado pintaba el total bajo la etiqueta "Subtotal".
        aRespuesta(pedido.subtotal()),
        aRespuesta(pedido.costoEnvio()),
        completa.total(),
        completa.creadoEn(),
        completa.datosTransferencia(),
        repositorioEnvios.buscarPorPedidoId(pedido.id()).map(this::aRespuesta).orElse(null),
        // `actor` y `motivo` siempre nulos: el comprador no tiene por qué ver qué administrador
        // operó cada transición ni la nota interna de por qué.
        completa.historial().stream()
            .map(h -> new HistorialPedidoRespuesta(h.estado(), h.fecha(), null, null))
            .toList(),
        repositorioSolicitudes.buscarPorPedidoId(pedido.id()).stream()
            .map(this::aRespuesta)
            .toList());
  }

  private EnvioPublicoRespuesta aRespuesta(Envio envio) {
    return new EnvioPublicoRespuesta(
        envio.guias().stream()
            .map(guia -> new GuiaPublicaRespuesta(guia.transportadora(), guia.numero()))
            .toList(),
        envio.despachadoEn());
  }

  /**
   * Del reintegro salen dos datos y nada más: cuánto volvió y cuándo. Ni el medio, ni el
   * comprobante, ni quién lo registró — el comprador ya sabe por dónde recibió su dinero, y el
   * actor es una nota interna. Mismo criterio que dejó el resto de este mapeador escrito campo a
   * campo.
   */
  private RetractoPublicoRespuesta aRespuesta(SolicitudRetracto solicitud) {
    Reintegro reintegro =
        solicitud.reintegroId().flatMap(repositorioReintegros::buscarPorId).orElse(null);
    return new RetractoPublicoRespuesta(
        solicitud.estado().name(),
        solicitud.radicadaEn(),
        solicitud.motivo().orElse(null),
        solicitud.productoRecibidoEn().orElse(null),
        solicitud.limiteDeReintegro().orElse(null),
        reintegro == null ? null : aRespuesta(reintegro.monto()),
        reintegro == null ? null : reintegro.registradoEn());
  }

  private DineroRespuesta aRespuesta(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }
}
