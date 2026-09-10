package co.tecnosport.api.presentation.reversion;

import co.tecnosport.api.application.reversion.RadicarReversion;
import co.tecnosport.api.application.reversion.RadicarReversionComando;
import co.tecnosport.api.application.reversion.RegistrarGestionReversion;
import co.tecnosport.api.application.reversion.RegistrarGestionReversionComando;
import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import co.tecnosport.api.application.reversion.ResolverReversion;
import co.tecnosport.api.application.reversion.ResolverReversionComando;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reversion.CausalReversion;
import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import co.tecnosport.api.presentation.reversion.dto.RadicarReversionRequest;
import co.tecnosport.api.presentation.reversion.dto.RegistrarGestionRequest;
import co.tecnosport.api.presentation.reversion.dto.ResolverReversionRequest;
import co.tecnosport.api.presentation.reversion.dto.SolicitudReversionRespuesta;
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
 * <p>Tres pasos y no dos, porque la ley pide tres cosas distintas: registrar la causal, facilitar
 * el tramite ante el emisor, y cerrar con el desenlace. Fundir la gestion en la resolucion borraria
 * la unica prueba de que se facilito algo.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReversionesControlador {

  private final RadicarReversion radicarReversion;
  private final RegistrarGestionReversion registrarGestion;
  private final ResolverReversion resolverReversion;
  private final RepositorioSolicitudesReversion repositorio;
  private final MapeadorRespuestasReversion mapeador;
  private final TransactionTemplate transaccion;

  public AdminReversionesControlador(
      RadicarReversion radicarReversion,
      RegistrarGestionReversion registrarGestion,
      ResolverReversion resolverReversion,
      RepositorioSolicitudesReversion repositorio,
      MapeadorRespuestasReversion mapeador,
      PlatformTransactionManager transactionManager) {
    this.radicarReversion = Objects.requireNonNull(radicarReversion);
    this.registrarGestion = Objects.requireNonNull(registrarGestion);
    this.resolverReversion = Objects.requireNonNull(resolverReversion);
    this.repositorio = Objects.requireNonNull(repositorio);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/pedidos/{pedidoId}/reversiones")
  public List<SolicitudReversionRespuesta> listar(@PathVariable UUID pedidoId) {
    return repositorio.buscarPorPedidoId(pedidoId).stream().map(mapeador::aRespuesta).toList();
  }

  @PostMapping("/pedidos/{pedidoId}/reversiones")
  @ResponseStatus(HttpStatus.CREATED)
  public SolicitudReversionRespuesta radicar(
      @PathVariable UUID pedidoId, @RequestBody RadicarReversionRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudReversion reversion =
        transaccion.execute(
            estado ->
                radicarReversion.ejecutar(
                    new RadicarReversionComando(
                        pedidoId,
                        CausalReversion.valueOf(cuerpo.causal()),
                        cuerpo.fechaDelHecho(),
                        cuerpo.recibidaEn(),
                        cuerpo.descripcion(),
                        actor)));
    return mapeador.aRespuesta(reversion);
  }

  @PostMapping("/reversiones/{id}/gestion")
  public SolicitudReversionRespuesta gestionar(
      @PathVariable UUID id, @RequestBody RegistrarGestionRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudReversion reversion =
        transaccion.execute(
            estado ->
                registrarGestion.ejecutar(
                    new RegistrarGestionReversionComando(id, cuerpo.gestion(), actor)));
    return mapeador.aRespuesta(reversion);
  }

  @PostMapping("/reversiones/{id}/resolucion")
  public SolicitudReversionRespuesta resolver(
      @PathVariable UUID id, @RequestBody ResolverReversionRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudReversion reversion =
        transaccion.execute(
            estado ->
                resolverReversion.ejecutar(
                    new ResolverReversionComando(
                        id,
                        DesenlaceReversion.valueOf(cuerpo.desenlace()),
                        cuerpo.resumenParaElComprador(),
                        cuerpo.monto(),
                        cuerpo.medio() == null ? null : MedioReintegro.valueOf(cuerpo.medio()),
                        cuerpo.comprobante(),
                        actor)));
    return mapeador.aRespuesta(reversion);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
