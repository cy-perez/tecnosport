package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.ConciliarTransferencia;
import co.tecnosport.api.application.pedido.ConciliarTransferenciaComando;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.DespacharPedidoComando;
import co.tecnosport.api.application.pedido.ListarPedidosAdmin;
import co.tecnosport.api.application.pedido.ListarPedidosAdminComando;
import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.VerificarContraentrega;
import co.tecnosport.api.application.pedido.VerificarContraentregaComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.presentation.pedido.dto.DespacharPedidoRequest;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidosPaginadosRespuesta;
import co.tecnosport.api.presentation.pedido.dto.VerificarContraentregaRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap) — este controlador no repite esa regla.
 *
 * <p>El actor de auditoría sale directo de {@code SecurityContextHolder} en vez de
 * {@code @AuthenticationPrincipal}: ese resolver solo queda registrado cuando
 * {@code @EnableWebSecurity} está activo (bootstrap), y {@code presentation} no depende de {@code
 * bootstrap} — ni en producción ni en sus pruebas {@code @WebMvcTest}. Mismo principal ({@code
 * UUID}) que {@code FiltroAutenticacionJwt} ya deja en el contexto.
 */
@RestController
@RequestMapping("/api/v1/admin/pedidos")
public class AdminPedidosControlador {

  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;

  private final ListarPedidosAdmin listarPedidosAdmin;
  private final ConciliarTransferencia conciliarTransferencia;
  private final VerificarContraentrega verificarContraentrega;
  private final DespacharPedido despacharPedido;
  private final MapeadorRespuestasPedido mapeador;
  private final TransactionTemplate transaccion;

  public AdminPedidosControlador(
      ListarPedidosAdmin listarPedidosAdmin,
      ConciliarTransferencia conciliarTransferencia,
      VerificarContraentrega verificarContraentrega,
      DespacharPedido despacharPedido,
      MapeadorRespuestasPedido mapeador,
      PlatformTransactionManager transactionManager) {
    this.listarPedidosAdmin = Objects.requireNonNull(listarPedidosAdmin);
    this.conciliarTransferencia = Objects.requireNonNull(conciliarTransferencia);
    this.verificarContraentrega = Objects.requireNonNull(verificarContraentrega);
    this.despacharPedido = Objects.requireNonNull(despacharPedido);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public PedidosPaginadosRespuesta listar(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    PedidosPaginados resultado =
        listarPedidosAdmin.ejecutar(new ListarPedidosAdminComando(pagina, tamano));
    return new PedidosPaginadosRespuesta(
        resultado.items().stream().map(mapeador::aRespuesta).toList(),
        resultado.pagina(),
        resultado.totalPaginas(),
        resultado.totalPedidos());
  }

  @PostMapping("/{id}/conciliar-transferencia")
  public PedidoRespuesta conciliar(@PathVariable UUID id) {
    String actor = "admin:" + actorId();
    Pedido pedido =
        transaccion.execute(
            estado ->
                conciliarTransferencia.ejecutar(new ConciliarTransferenciaComando(id, actor)));
    return mapeador.aRespuesta(pedido);
  }

  @PostMapping("/{id}/verificar-contraentrega")
  public PedidoRespuesta verificar(
      @PathVariable UUID id, @RequestBody VerificarContraentregaRequest cuerpo) {
    String actor = "admin:" + actorId();
    Pedido pedido =
        transaccion.execute(
            estado ->
                verificarContraentrega.ejecutar(
                    new VerificarContraentregaComando(id, actor, cuerpo.motivo())));
    return mapeador.aRespuesta(pedido);
  }

  @PostMapping("/{id}/despacho")
  public PedidoRespuesta despachar(
      @PathVariable UUID id, @RequestBody DespacharPedidoRequest cuerpo) {
    String actor = "admin:" + actorId();
    Pedido pedido =
        transaccion.execute(
            estado ->
                despacharPedido.ejecutar(
                    new DespacharPedidoComando(
                        id,
                        cuerpo.transportadora(),
                        cuerpo.guia(),
                        Dinero.deCop(cuerpo.costoEnvio()),
                        actor)));
    return mapeador.aRespuesta(pedido);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
