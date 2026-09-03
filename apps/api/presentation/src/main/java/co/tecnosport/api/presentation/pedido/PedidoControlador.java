package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.CrearPedidoComando;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sin autenticación todavía: {@code usuarioId} siempre nulo, igual que {@code CarritoControlador}
 * (Fase 4, el correo identifica al comprador invitado). {@code CrearPedido.ejecutar} reserva
 * inventario con bloqueo pesimista y guarda el pedido en la misma llamada — ambas escrituras tienen
 * que caer en una sola transacción, y este controlador es quien la abre (ver el javadoc de {@code
 * RepositorioPedidosJpa} sobre por qué el adaptador no puede abrirla por su cuenta).
 */
@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoControlador {

  private final CrearPedido crearPedido;
  private final MapeadorRespuestasPedido mapeador;
  private final TransactionTemplate transaccion;

  public PedidoControlador(
      CrearPedido crearPedido,
      MapeadorRespuestasPedido mapeador,
      PlatformTransactionManager transactionManager) {
    this.crearPedido = Objects.requireNonNull(crearPedido);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  public PedidoRespuesta crear(@RequestBody CrearPedidoRequest cuerpo) {
    CrearPedidoComando comando = aComando(cuerpo);
    Pedido pedido = transaccion.execute(estado -> crearPedido.ejecutar(comando));
    return mapeador.aRespuesta(pedido);
  }

  private CrearPedidoComando aComando(CrearPedidoRequest cuerpo) {
    List<CrearPedidoComando.LineaComando> lineas =
        cuerpo.lineas().stream()
            .map(l -> new CrearPedidoComando.LineaComando(l.varianteId(), l.cantidad()))
            .toList();
    Direccion direccion = cuerpo.direccion() == null ? null : aDireccion(cuerpo.direccion());
    return new CrearPedidoComando(
        null,
        cuerpo.correo(),
        lineas,
        TipoEntrega.valueOf(cuerpo.tipoEntrega()),
        direccion,
        MetodoPago.valueOf(cuerpo.metodoPago()));
  }

  private Direccion aDireccion(CrearPedidoRequest.DireccionRequest d) {
    return new Direccion(
        d.codigoDaneDepartamento(),
        d.departamento(),
        d.codigoDaneCiudad(),
        d.ciudad(),
        d.direccion(),
        d.indicaciones());
  }
}
