package co.tecnosport.api.presentation.garantia;

import co.tecnosport.api.application.garantia.RadicarReclamacionGarantia;
import co.tecnosport.api.application.garantia.RadicarReclamacionGarantiaComando;
import co.tecnosport.api.application.garantia.RepositorioReclamacionesGarantia;
import co.tecnosport.api.application.garantia.ResolverGarantia;
import co.tecnosport.api.application.garantia.ResolverGarantiaComando;
import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.presentation.garantia.dto.RadicarGarantiaRequest;
import co.tecnosport.api.presentation.garantia.dto.ReclamacionGarantiaRespuesta;
import co.tecnosport.api.presentation.garantia.dto.ResolverGarantiaRequest;
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
 * <p>Radicar cuelga del pedido porque una garantia se reclama sobre algo que se compro y se
 * entrego; resolver cuelga de la reclamacion, que es lo que avanza. Mismo criterio que el retracto.
 *
 * <p>Sin cabecera de idempotencia: el propio agregado rechaza resolver dos veces, y radicar deja
 * una solicitud de atencion nueva que el panel muestra — un doble clic se ve, no se esconde.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminGarantiasControlador {

  private final RadicarReclamacionGarantia radicarGarantia;
  private final ResolverGarantia resolverGarantia;
  private final RepositorioReclamacionesGarantia repositorio;
  private final MapeadorRespuestasGarantia mapeador;
  private final TransactionTemplate transaccion;

  public AdminGarantiasControlador(
      RadicarReclamacionGarantia radicarGarantia,
      ResolverGarantia resolverGarantia,
      RepositorioReclamacionesGarantia repositorio,
      MapeadorRespuestasGarantia mapeador,
      PlatformTransactionManager transactionManager) {
    this.radicarGarantia = Objects.requireNonNull(radicarGarantia);
    this.resolverGarantia = Objects.requireNonNull(resolverGarantia);
    this.repositorio = Objects.requireNonNull(repositorio);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/pedidos/{pedidoId}/garantias")
  public List<ReclamacionGarantiaRespuesta> listar(@PathVariable UUID pedidoId) {
    return repositorio.buscarPorPedidoId(pedidoId).stream().map(mapeador::aRespuesta).toList();
  }

  @PostMapping("/pedidos/{pedidoId}/garantias")
  @ResponseStatus(HttpStatus.CREATED)
  public ReclamacionGarantiaRespuesta radicar(
      @PathVariable UUID pedidoId, @RequestBody RadicarGarantiaRequest cuerpo) {
    String actor = "admin:" + actorId();
    ReclamacionGarantia reclamacion =
        transaccion.execute(
            estado ->
                radicarGarantia.ejecutar(
                    new RadicarReclamacionGarantiaComando(
                        pedidoId,
                        cuerpo.varianteId(),
                        cuerpo.recibidaEn(),
                        cuerpo.descripcionDelFallo(),
                        actor)));
    return mapeador.aRespuesta(reclamacion);
  }

  @PostMapping("/garantias/{id}/resolucion")
  public ReclamacionGarantiaRespuesta resolver(
      @PathVariable UUID id, @RequestBody ResolverGarantiaRequest cuerpo) {
    String actor = "admin:" + actorId();
    ReclamacionGarantia reclamacion =
        transaccion.execute(
            estado ->
                resolverGarantia.ejecutar(
                    new ResolverGarantiaComando(
                        id,
                        DesenlaceGarantia.valueOf(cuerpo.desenlace()),
                        cuerpo.resumenParaElComprador(),
                        cuerpo.monto(),
                        cuerpo.medio() == null ? null : MedioReintegro.valueOf(cuerpo.medio()),
                        cuerpo.comprobante(),
                        actor)));
    return mapeador.aRespuesta(reclamacion);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
