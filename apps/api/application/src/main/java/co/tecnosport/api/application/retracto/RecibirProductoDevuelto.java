package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.time.Instant;
import java.util.Objects;

/**
 * La mercancía volvió: el pedido pasa a {@code DEVUELTO}, cada línea vuelve al inventario y la
 * solicitud queda a la espera del reembolso.
 *
 * <p>Es el momento en que la obligación cambia de lado. Hasta aquí se esperaba al comprador; desde
 * aquí corre contra el negocio el plazo de quince días calendario del reintegro (Ley 1480 de 2011,
 * art. 47, modificado por la Ley 2439 de 2024), y por eso la solicitud guarda la fecha.
 *
 * <p>Las transiciones van antes de tocar el inventario, mismo criterio que {@code
 * RechazarEnEntrega} y {@code DespacharPedido}: un segundo intento se bloquea en la máquina de
 * estados y no llega a mover existencias dos veces. Lo que devuelve cada línea al almacén es {@code
 * Inventario.devolver}, que decide entre entrada y liberación según cómo quedó la reserva — un
 * contraentrega llega aquí con la suya todavía abierta.
 */
public final class RecibirProductoDevuelto {

  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public RecibirProductoDevuelto(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudRetracto ejecutar(RecibirProductoDevueltoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudRetracto solicitud =
        repositorioSolicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(() -> new SolicitudRetractoNoEncontradaException(comando.solicitudId()));
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(solicitud.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(solicitud.pedidoId()));

    Instant ahora = reloj.ahora();
    solicitud.recibirProducto(ahora);
    pedido.transicionar(EstadoPedido.DEVUELTO, comando.actor(), "retracto", ahora);
    for (LineaPedido linea : pedido.lineas()) {
      devolverAlInventario(linea, ahora);
    }

    repositorioSolicitudes.guardar(solicitud);
    repositorioPedidos.guardar(pedido);
    return solicitud;
  }

  private void devolverAlInventario(LineaPedido linea, Instant ahora) {
    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(linea.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
    inventario.devolver(linea.idReserva(), "retracto", ahora);
    repositorioInventario.guardar(inventario);
  }
}
