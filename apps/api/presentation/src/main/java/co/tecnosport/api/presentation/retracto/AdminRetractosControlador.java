package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.retracto.RecibirProductoDevuelto;
import co.tecnosport.api.application.retracto.RecibirProductoDevueltoComando;
import co.tecnosport.api.application.retracto.RegistrarReintegro;
import co.tecnosport.api.application.retracto.RegistrarReintegroComando;
import co.tecnosport.api.application.retracto.RegistrarRetracto;
import co.tecnosport.api.application.retracto.RegistrarRetractoComando;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.presentation.retracto.dto.RegistrarReintegroRequest;
import co.tecnosport.api.presentation.retracto.dto.RegistrarRetractoRequest;
import co.tecnosport.api.presentation.retracto.dto.SolicitudRetractoRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}.
 *
 * <p>Radicar cuelga del pedido porque una solicitud no existe sin el; los pasos siguientes cuelgan
 * de la solicitud, que es lo que avanza. El actor sale de {@code SecurityContextHolder} directo,
 * mismo motivo que {@code AdminPedidosControlador}.
 *
 * <p>Sin cabecera de idempotencia: la maquina de estados de la solicitud ya hace idempotentes las
 * acciones administrativas de un solo actor (docs/03-api.md), y radicar dos veces lo bloquea la
 * guarda de "una sola en curso".
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRetractosControlador {

  private final RegistrarRetracto registrarRetracto;
  private final RecibirProductoDevuelto recibirProductoDevuelto;
  private final RegistrarReintegro registrarReintegro;
  private final RepositorioSolicitudesRetracto repositorioSolicitudes;
  private final RepositorioReintegros repositorioReintegros;
  private final MapeadorRespuestasRetracto mapeador;
  private final TransactionTemplate transaccion;

  public AdminRetractosControlador(
      RegistrarRetracto registrarRetracto,
      RecibirProductoDevuelto recibirProductoDevuelto,
      RegistrarReintegro registrarReintegro,
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioReintegros repositorioReintegros,
      MapeadorRespuestasRetracto mapeador,
      PlatformTransactionManager transactionManager) {
    this.registrarRetracto = Objects.requireNonNull(registrarRetracto);
    this.recibirProductoDevuelto = Objects.requireNonNull(recibirProductoDevuelto);
    this.registrarReintegro = Objects.requireNonNull(registrarReintegro);
    this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/pedidos/{pedidoId}/retractos")
  public List<SolicitudRetractoRespuesta> listar(@PathVariable UUID pedidoId) {
    return repositorioSolicitudes.buscarPorPedidoId(pedidoId).stream()
        .map(this::aRespuestaConSuReintegro)
        .toList();
  }

  @PostMapping("/pedidos/{pedidoId}/retractos")
  @ResponseStatus(HttpStatus.CREATED)
  public SolicitudRetractoRespuesta radicar(
      @PathVariable UUID pedidoId, @RequestBody(required = false) RegistrarRetractoRequest cuerpo) {
    String actor = "admin:" + actorId();
    String motivo = cuerpo == null ? null : cuerpo.motivo();
    MedioReintegro medioPreferido = medioDe(cuerpo == null ? null : cuerpo.medioPreferido());
    SolicitudRetracto solicitud =
        transaccion.execute(
            estado ->
                registrarRetracto.ejecutar(
                    new RegistrarRetractoComando(pedidoId, motivo, medioPreferido, actor)));
    return aRespuestaConSuReintegro(solicitud);
  }

  @PostMapping("/retractos/{id}/recepcion")
  public SolicitudRetractoRespuesta recibirProducto(@PathVariable UUID id) {
    String actor = "admin:" + actorId();
    SolicitudRetracto solicitud =
        transaccion.execute(
            estado ->
                recibirProductoDevuelto.ejecutar(new RecibirProductoDevueltoComando(id, actor)));
    return aRespuestaConSuReintegro(solicitud);
  }

  @PostMapping("/retractos/{id}/reintegro")
  public SolicitudRetractoRespuesta reintegrar(
      @PathVariable UUID id, @RequestBody RegistrarReintegroRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudRetracto solicitud =
        transaccion.execute(
            estado ->
                registrarReintegro.ejecutar(
                    new RegistrarReintegroComando(
                        id,
                        cuerpo.monto(),
                        MedioReintegro.valueOf(cuerpo.medio()),
                        medioDe(cuerpo.medioPreferido()),
                        cuerpo.comprobante(),
                        actor)));
    return aRespuestaConSuReintegro(solicitud);
  }

  /**
   * Un medio opcional que llega como texto. Vacio y ausente son lo mismo aqui —"no lo dijo"— y un
   * valor que no existe en el enum revienta con {@code IllegalArgumentException}, que {@code
   * ManejadorDeErrores} ya traduce a 400: es un cuerpo mal formado, no un caso de negocio.
   */
  private MedioReintegro medioDe(String valor) {
    return valor == null || valor.isBlank() ? null : MedioReintegro.valueOf(valor);
  }

  /** Una consulta mas por solicitud, y a proposito: la constancia es otro agregado. */
  private SolicitudRetractoRespuesta aRespuestaConSuReintegro(SolicitudRetracto solicitud) {
    Reintegro reintegro =
        solicitud.reintegroId().flatMap(repositorioReintegros::buscarPorId).orElse(null);
    return mapeador.aRespuesta(solicitud, reintegro);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
