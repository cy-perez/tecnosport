package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.MetodosDePagoDisponiblesComando;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedido;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedidoComando;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPorNumero;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPorNumeroComando;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.CrearPedidoComando;
import co.tecnosport.api.application.pedido.ReintentarPago;
import co.tecnosport.api.application.pedido.ReintentarPagoComando;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.compartido.IpDelCliente;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import co.tecnosport.api.presentation.pedido.dto.MetodosDePagoDisponiblesRequest;
import co.tecnosport.api.presentation.pedido.dto.PedidoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.PedidoSeguimientoRespuesta;
import co.tecnosport.api.presentation.pedido.dto.ReintentarPagoRequest;
import co.tecnosport.api.presentation.pedido.dto.SeguimientoPorNumeroRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
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
  private final ConsultarSeguimientoPorNumero consultarSeguimientoPorNumero;
  private final MapeadorRespuestasPedido mapeador;
  private final MapeadorSeguimiento mapeadorSeguimiento;
  private final TransactionTemplate transaccion;

  public PedidoControlador(
      CrearPedido crearPedido,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      ReintentarPago reintentarPago,
      ConsultarSeguimientoPedido consultarSeguimientoPedido,
      ConsultarSeguimientoPorNumero consultarSeguimientoPorNumero,
      MapeadorRespuestasPedido mapeador,
      MapeadorSeguimiento mapeadorSeguimiento,
      PlatformTransactionManager transactionManager) {
    this.mapeadorSeguimiento = Objects.requireNonNull(mapeadorSeguimiento);
    this.crearPedido = Objects.requireNonNull(crearPedido);
    this.metodosDePagoDisponibles = Objects.requireNonNull(metodosDePagoDisponibles);
    this.reintentarPago = Objects.requireNonNull(reintentarPago);
    this.consultarSeguimientoPedido = Objects.requireNonNull(consultarSeguimientoPedido);
    this.consultarSeguimientoPorNumero = Objects.requireNonNull(consultarSeguimientoPorNumero);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  public PedidoRespuesta crear(
      @RequestBody CrearPedidoRequest cuerpo, HttpServletRequest peticion) {
    CrearPedidoComando comando = aComando(cuerpo, IpDelCliente.de(peticion));
    Pedido pedido = transaccion.execute(estado -> crearPedido.ejecutar(comando));
    return mapeador.aRespuesta(pedido);
  }

  @PostMapping("/metodos-de-pago-disponibles")
  public List<MetodoPago> metodosDePagoDisponibles(
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
    // Por NOMBRE y no por el orden del enum. Es lo que hacia `map(Enum::name).sorted()` cuando
    // la lista viajaba como cadenas, y este es el orden en que el checkout pinta los botones:
    // dejar que Jackson serialice el enum en su orden de declaracion los habria movido de sitio
    // sin que nadie lo pidiera.
    return metodosDePagoDisponibles.ejecutar(comando).stream()
        .sorted(Comparator.comparing(Enum::name))
        .toList();
  }

  @PostMapping("/{id}/reintentar-pago")
  public PedidoRespuesta reintentarPago(
      @PathVariable UUID id, @RequestBody ReintentarPagoRequest cuerpo) {
    Pedido pedido =
        transaccion.execute(
            estado -> reintentarPago.ejecutar(new ReintentarPagoComando(id, cuerpo.correo())));
    return mapeador.aRespuesta(pedido);
  }

  @GetMapping("/{id}/seguimiento")
  public PedidoSeguimientoRespuesta seguimiento(
      @PathVariable UUID id, @RequestParam String correo) {
    Pedido pedido =
        consultarSeguimientoPedido.ejecutar(new ConsultarSeguimientoPedidoComando(id, correo));
    return mapeadorSeguimiento.aRespuesta(pedido);
  }

  /**
   * El mismo seguimiento, entrando por el número legible del pedido: es el único identificador que
   * el comprador tiene, porque el {@code id} es un UUID que no aparece en nada que una persona lea.
   * Es lo que sostiene el formulario de "Estado del pedido" del pie.
   *
   * <p>{@code POST} aunque no cree nada, y la ruta es literal —{@code /seguimiento}, sin {@code
   * &#123;id&#125;} delante—, así que no compite con el {@code GET} de arriba. Lo de POST es por el
   * cuerpo: ver {@link SeguimientoPorNumeroRequest}, el correo no viaja en la URL.
   *
   * <p>Sin {@code TransactionTemplate}: es una lectura, y las lecturas de este controlador no abren
   * transacción propia — el hermano de arriba tampoco.
   *
   * <p><b>Va detrás del límite de intentos por IP</b>, con su propio presupuesto y más estrecho que
   * el de los demás ({@code ConfiguracionLimiteIntentos}): el número es secuencial y adivinable, y
   * lo único que protege el pedido es el correo.
   */
  @PostMapping("/seguimiento")
  public PedidoSeguimientoRespuesta seguimientoPorNumero(
      @RequestBody SeguimientoPorNumeroRequest cuerpo) {
    Pedido pedido =
        consultarSeguimientoPorNumero.ejecutar(
            new ConsultarSeguimientoPorNumeroComando(cuerpo.numeroPedido(), cuerpo.correo()));
    // Sin el `id`: ver el javadoc de `aRespuestaSinIdInterno`. Ese UUID es la credencial de
    // `/pagos/intentos` y de `/pedidos/{id}/reintentar-pago`, y entregarlo por una puerta que se
    // abre adivinando un número secuencial convierte una fuga de lectura en una de escritura.
    return mapeadorSeguimiento.aRespuestaSinIdInterno(pedido);
  }

  private CrearPedidoComando aComando(CrearPedidoRequest cuerpo, String direccionIp) {
    List<CrearPedidoComando.LineaComando> lineas =
        cuerpo.lineas().stream()
            .map(l -> new CrearPedidoComando.LineaComando(l.varianteId(), l.cantidad()))
            .toList();
    Direccion direccion = cuerpo.direccion() == null ? null : aDireccion(cuerpo.direccion());
    return new CrearPedidoComando(
        null,
        cuerpo.correo(),
        new Contacto(cuerpo.nombre(), cuerpo.telefono()),
        lineas,
        TipoEntrega.valueOf(cuerpo.tipoEntrega()),
        direccion,
        cuerpo.metodoPago(),
        cuerpo.autorizaDatos(),
        direccionIp);
  }

  private Direccion aDireccion(CrearPedidoRequest.DireccionRequest d) {
    return new Direccion(
        d.codigoDaneDepartamento(),
        d.departamento(),
        d.codigoDaneCiudad(),
        d.ciudad(),
        d.direccion(),
        d.indicaciones(),
        d.barrio());
  }
}
