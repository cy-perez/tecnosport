package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.MetodosDePagoDisponiblesComando;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedido;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedidoComando;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.CrearPedidoComando;
import co.tecnosport.api.application.pedido.ReintentarPago;
import co.tecnosport.api.application.pedido.ReintentarPagoComando;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import co.tecnosport.api.presentation.pedido.dto.MetodosDePagoDisponiblesRequest;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final MetodosDePagoDisponibles metodosDePagoDisponibles;
  private final ReintentarPago reintentarPago;
  private final ConsultarSeguimientoPedido consultarSeguimientoPedido;
  private final MapeadorRespuestasPedido mapeador;
  private final TransactionTemplate transaccion;

  public PedidoControlador(
      CrearPedido crearPedido,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      ReintentarPago reintentarPago,
      ConsultarSeguimientoPedido consultarSeguimientoPedido,
      MapeadorRespuestasPedido mapeador,
      PlatformTransactionManager transactionManager) {
    this.crearPedido = Objects.requireNonNull(crearPedido);
    this.metodosDePagoDisponibles = Objects.requireNonNull(metodosDePagoDisponibles);
    this.reintentarPago = Objects.requireNonNull(reintentarPago);
    this.consultarSeguimientoPedido = Objects.requireNonNull(consultarSeguimientoPedido);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  public PedidoRespuesta crear(@RequestBody CrearPedidoRequest cuerpo) {
    CrearPedidoComando comando = aComando(cuerpo);
    Pedido pedido = transaccion.execute(estado -> crearPedido.ejecutar(comando));
    return mapeador.aRespuesta(pedido);
  }

  @PostMapping("/metodos-de-pago-disponibles")
  public List<String> metodosDePagoDisponibles(
      @RequestBody MetodosDePagoDisponiblesRequest cuerpo) {
    MetodosDePagoDisponiblesComando comando =
        new MetodosDePagoDisponiblesComando(
            cuerpo.lineas().stream()
                .map(
                    l ->
                        new MetodosDePagoDisponiblesComando.LineaComando(
                            l.varianteId(), l.cantidad()))
                .toList(),
            cuerpo.correo(),
            TipoEntrega.valueOf(cuerpo.tipoEntrega()),
            cuerpo.direccion() == null ? null : aDireccion(cuerpo.direccion()));
    return metodosDePagoDisponibles.ejecutar(comando).stream().map(Enum::name).sorted().toList();
  }

  @PostMapping("/{id}/reintentar-pago")
  public PedidoRespuesta reintentarPago(@PathVariable UUID id) {
    Pedido pedido =
        transaccion.execute(estado -> reintentarPago.ejecutar(new ReintentarPagoComando(id)));
    return mapeador.aRespuesta(pedido);
  }

  @GetMapping("/{id}/seguimiento")
  public PedidoRespuesta seguimiento(@PathVariable UUID id, @RequestParam String correo) {
    Pedido pedido =
        consultarSeguimientoPedido.ejecutar(new ConsultarSeguimientoPedidoComando(id, correo));
    return mapeador.aRespuestaPublica(pedido);
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
