package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * La mercancía de un pedido rechazado en la entrega volvió al almacén: {@code RECHAZADO_EN_ENTREGA
 * -> DEVUELTO}.
 *
 * <p>Hace las dos cosas que el rechazo no podía hacer porque el paquete todavía venía en camino:
 *
 * <ul>
 *   <li><b>Reingresa cada unidad</b> por {@code Inventario.devolver}, que decide entre {@code
 *       ENTRADA} (el pago ya había confirmado la reserva) y {@code LIBERACION} (un contraentrega la
 *       tiene abierta).
 *   <li><b>Devuelve lo que entró</b>, entero: el comprador no recibió nada, así que no hay producto
 *       ni flete que descontar. El monto no lo teclea nadie —regla dura #7—: es lo que el pedido
 *       cobró ({@code Pedido.dineroRecibido}) menos lo que ya volvió por otros caminos ({@link
 *       TopeDeReintegro}). Un contraentrega rechazado nunca cobró, y no deja constancia.
 * </ul>
 *
 * <p>La transición va primero porque es la que valida: un segundo intento sobre un pedido ya
 * devuelto se bloquea ahí, antes de reingresar dos veces la misma unidad.
 *
 * <p>Lo que no cubre, y queda dicho: un paquete que la transportadora pierde no vuelve nunca, y
 * entonces nadie pulsa esto. Ese reintegro sigue debiéndose; es un reclamo a la transportadora con
 * su propio camino.
 */
public final class RecibirPedidoRechazado {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioReintegros repositorioReintegros;
  private final TopeDeReintegro tope;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public RecibirPedidoRechazado(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.tope = Objects.requireNonNull(tope);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(RecibirPedidoRechazadoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));

    Dinero porDevolver = porDevolver(pedido);
    boolean hayQueDevolver = porDevolver.valor().signum() > 0;
    if (hayQueDevolver && comando.medio() == null) {
      throw ReintegroRequeridoException.porqueElPedidoRechazadoYaHabiaCobrado(pedido.id());
    }

    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.DEVUELTO, comando.actor(), "volvió al almacén tras el rechazo", ahora);
    for (LineaPedido linea : pedido.lineas()) {
      reingresar(linea, ahora);
    }
    if (hayQueDevolver) {
      tope.exigirQueQuepa(pedido.id(), pedido.total(), porDevolver);
      repositorioReintegros.guardar(
          Reintegro.registrar(
              pedido.id(),
              MotivoReintegro.RECHAZO_EN_ENTREGA,
              pedido.id(),
              porDevolver,
              comando.medio(),
              comando.comprobante(),
              ahora,
              comando.actor()));
    }
    repositorioPedidos.guardar(pedido);
    avisar(pedido, hayQueDevolver ? comando.medio() : null);
    return pedido;
  }

  private Dinero porDevolver(Pedido pedido) {
    BigDecimal entro = pedido.dineroRecibido().valor();
    BigDecimal yaVolvio = tope.yaDevuelto(pedido.id()).valor();
    return Dinero.deCop(entro.subtract(yaVolvio).max(BigDecimal.ZERO));
  }

  private void reingresar(LineaPedido linea, Instant ahora) {
    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(linea.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
    inventario.devolver(linea.idReserva(), "volvió tras el rechazo en la entrega", ahora);
    repositorioInventario.guardar(inventario);
  }

  /**
   * Un reintegro por Sistecrédito no devuelve dinero: anula un crédito, y el comprador necesita
   * leer que deje de pagar cuotas. Por eso tiene su propio texto, como en el retracto.
   */
  private void avisar(Pedido pedido, MedioReintegro medio) {
    TextoDeCorreo dinero =
        medio == null
            ? TextoDeCorreo.PEDIDO_CANCELACION_SIN_COBRO
            : medio == MedioReintegro.SISTECREDITO
                ? TextoDeCorreo.PEDIDO_DEVUELTO_REINTEGRO_SISTECREDITO
                : TextoDeCorreo.PEDIDO_CANCELACION_CON_REINTEGRO;
    try {
      enviadorDeCorreo.enviar(
          pedido.correo(),
          textos.texto(TextoDeCorreo.PEDIDO_DEVUELTO_ASUNTO, pedido.numeroPedido().valor()),
          textos.texto(TextoDeCorreo.PEDIDO_DEVUELTO_CUERPO)
              + textos.texto(dinero)
              + textos.texto(TextoDeCorreo.PEDIDO_CANCELACION_CIERRE));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // Mismo criterio que CancelarPedido (adr/0045): encolar se une a esta transacción, así que
      // relanzar no protege nada y taparía el error real.
    }
  }
}
