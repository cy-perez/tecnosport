package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.MetodosDePagoDisponiblesComando;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedido;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedidoComando;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.CrearPedidoComando;
import co.tecnosport.api.application.pedido.ReintentarPago;
import co.tecnosport.api.application.pedido.ReintentarPagoComando;
import co.tecnosport.api.application.pedido.SolicitarEnlaceDeSeguimiento;
import co.tecnosport.api.application.pedido.SolicitarEnlaceDeSeguimientoComando;
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
import co.tecnosport.api.presentation.pedido.dto.SeguimientoPorIdRequest;
import co.tecnosport.api.presentation.pedido.dto.SeguimientoPorNumeroRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
  private final SolicitarEnlaceDeSeguimiento solicitarEnlaceDeSeguimiento;
  private final MapeadorRespuestasPedido mapeador;
  private final MapeadorSeguimiento mapeadorSeguimiento;
  private final TransactionTemplate transaccion;

  public PedidoControlador(
      CrearPedido crearPedido,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      ReintentarPago reintentarPago,
      ConsultarSeguimientoPedido consultarSeguimientoPedido,
      SolicitarEnlaceDeSeguimiento solicitarEnlaceDeSeguimiento,
      MapeadorRespuestasPedido mapeador,
      MapeadorSeguimiento mapeadorSeguimiento,
      PlatformTransactionManager transactionManager) {
    this.mapeadorSeguimiento = Objects.requireNonNull(mapeadorSeguimiento);
    this.crearPedido = Objects.requireNonNull(crearPedido);
    this.metodosDePagoDisponibles = Objects.requireNonNull(metodosDePagoDisponibles);
    this.reintentarPago = Objects.requireNonNull(reintentarPago);
    this.consultarSeguimientoPedido = Objects.requireNonNull(consultarSeguimientoPedido);
    this.solicitarEnlaceDeSeguimiento = Objects.requireNonNull(solicitarEnlaceDeSeguimiento);
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
            cuerpo.direccion() == null ? null : aDireccion(cuerpo.direccion()),
            null,
            cuerpo.telefono(),
            cuerpo.transportadora());
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

  /**
   * {@code POST} y no {@code GET}, desde el 4 de octubre de 2026: el correo que autoriza viajaba
   * como {@code ?correo=} y quedaba en los registros de la web y de la API. No crea nada; el método
   * es por el cuerpo, como el seguimiento por número.
   */
  @PostMapping("/{id}/seguimiento")
  public PedidoSeguimientoRespuesta seguimiento(
      @PathVariable UUID id, @RequestBody SeguimientoPorIdRequest cuerpo) {
    Pedido pedido =
        consultarSeguimientoPedido.ejecutar(
            new ConsultarSeguimientoPedidoComando(id, cuerpo.correo()));
    return mapeadorSeguimiento.aRespuesta(pedido);
  }

  /**
   * El formulario de "Estado del pedido" del pie, entrando por el número legible: es el único
   * identificador que el comprador tiene, porque el {@code id} es un UUID que no aparece en nada
   * que una persona lea.
   *
   * <p><b>Desde el 25 de octubre de 2026 no devuelve el pedido</b> (ADR-0088): si el número y el
   * correo coinciden, le manda a ese correo el enlace a la pantalla de estado, y responde 202 sin
   * cuerpo en todos los casos. Devolverlo dejaba que quien conociera el correo de alguien
   * recorriera números —van en orden— hasta dar con un pedido suyo.
   *
   * <p>{@code POST} y ruta literal —{@code /seguimiento}, sin {@code &#123;id&#125;} delante—, así
   * que no compite con el de arriba. El correo va en el cuerpo, no en la URL: ver {@link
   * SeguimientoPorNumeroRequest}.
   *
   * <p>Con transacción, a diferencia del de arriba: escribe en el limitador y en la bandeja de
   * salida. Y sigue detrás del límite por IP de {@code ConfiguracionLimiteIntentos}, que ahora
   * frena a quien recorre números y el tope por pedido protege al titular.
   */
  @PostMapping("/seguimiento")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void seguimientoPorNumero(@RequestBody SeguimientoPorNumeroRequest cuerpo) {
    transaccion.executeWithoutResult(
        estado ->
            solicitarEnlaceDeSeguimiento.ejecutar(
                new SolicitarEnlaceDeSeguimientoComando(cuerpo.numeroPedido(), cuerpo.correo())));
  }

  private CrearPedidoComando aComando(CrearPedidoRequest cuerpo, String direccionIp) {
    List<CrearPedidoComando.LineaComando> lineas =
        cuerpo.lineas().stream()
            .map(
                l ->
                    new CrearPedidoComando.LineaComando(
                        l.varianteId(), l.cantidad(), l.enlaceCompra()))
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
        direccionIp,
        cuerpo.transportadora());
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
