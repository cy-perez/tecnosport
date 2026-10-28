package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * El pedido de recogida ya está en el punto: pasa a {@code LISTO_PARA_RECOGER} y se le avisa al
 * comprador (ADR-0094). Es el despacho de la recogida, sin guía ni transportadora.
 *
 * <p>El aviso dice lo que el comprador necesita para venir: dónde, en qué horario, que muestre su
 * número de pedido, cuánto paga si paga en el punto y hasta cuándo se le guarda. Desde este momento
 * corren las 48 horas de {@link Pedido#PLAZO_PARA_RECOGER}, y no desde la compra: lo que el
 * proveedor tarde en traerlo no le corre al comprador.
 */
public final class MarcarListoParaRecoger {

  private static final DateTimeFormatter FECHA_Y_HORA =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private final RepositorioPedidos repositorioPedidos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final String urlBaseEstado;

  public MarcarListoParaRecoger(
      RepositorioPedidos repositorioPedidos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      String urlBaseEstado) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.urlBaseEstado = Objects.requireNonNull(urlBaseEstado);
  }

  public Pedido ejecutar(MarcarListoParaRecogerComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    // Un envío a domicilio no queda listo para recoger: lo rechaza el agregado (ADR-0094).
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.LISTO_PARA_RECOGER, comando.actor(), "listo para recoger en el punto", ahora);
    repositorioPedidos.guardar(pedido);
    avisarAlComprador(pedido);
    return pedido;
  }

  private void avisarAlComprador(Pedido pedido) {
    String numero = pedido.numeroPedido().valor();
    String venceEn =
        FECHA_Y_HORA.format(pedido.recogidaVenceEn().orElseThrow().atZone(ZonaDelNegocio.ZONA));
    boolean pagaAlRecoger = pedido.metodoPago() == MetodoPago.PAGO_EN_PUNTO;
    String pago =
        pagaAlRecoger
            ? textos.texto(
                TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_PAGO_EN_PUNTO,
                textos.dinero(pedido.total()))
            : textos.texto(TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_PAGADO);
    String vencimiento =
        pagaAlRecoger
            ? textos.texto(TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_PLAZO_SIN_COBRO, venceEn)
            : textos.texto(TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_PLAZO_CON_REINTEGRO, venceEn);
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          pedido.nombreParaSaludar().orElse(null),
          textos.texto(TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_ASUNTO, numero),
          textos.texto(TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_CUERPO, numero)
              + pago
              + vencimiento
              + textos.texto(
                  TextoDeCorreo.PEDIDO_LISTO_PARA_RECOGER_CIERRE,
                  EnlaceDeEstado.de(urlBaseEstado, pedido)));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que DespacharPedido (adr/0045): encolar se une a esta transacción.
    }
  }
}
