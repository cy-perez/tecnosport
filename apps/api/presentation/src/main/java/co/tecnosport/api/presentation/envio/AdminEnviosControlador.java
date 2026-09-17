package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.AcusarRevisionDeEmision;
import co.tecnosport.api.application.envio.AcusarRevisionDeEmisionComando;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuia;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuiaComando;
import co.tecnosport.api.application.envio.ListarEnviosEnRevision;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.presentation.envio.dto.AcusarRevisionRequest;
import co.tecnosport.api.presentation.envio.dto.AcuseDeRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.BandejaDeRevisionRespuesta;
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
 * La bandeja de revisión de envíos, bajo {@code /api/v1/admin/**} y protegida por rol ADMIN en
 * {@code ConfiguracionSeguridad}.
 *
 * <p>Controlador propio y no un método más de los dos que ya hay en este paquete: la cotización y
 * el webhook son públicos —uno lo llama el checkout, el otro la plataforma— y mezclar en la misma
 * clase rutas con y sin rol es la forma más fácil de que una acabe en el grupo equivocado.
 *
 * <p>Sin cabecera de idempotencia, mismo criterio que el resto del panel (docs/03-api.md): acusar
 * dos veces no cobra nada ni mueve nada, solo deja dos filas de rastro, que es exactamente lo que
 * pasó.
 */
@RestController
@RequestMapping("/api/v1/admin/envios")
public class AdminEnviosControlador {

  /**
   * El tope del lote. Es una pantalla de diagnóstico, no un listado paginado: si lo que hay que
   * mirar no cabe en cien filas, el problema ya no es de paginación.
   */
  private static final int MAXIMO_PREDETERMINADO = 100;

  private final ListarEnviosEnRevision listarEnRevision;
  private final AcusarRevisionDeGuia acusarGuia;
  private final AcusarRevisionDeEmision acusarEmision;
  private final MapeadorBandejaDeRevision mapeador;
  private final TransactionTemplate transaccion;

  public AdminEnviosControlador(
      ListarEnviosEnRevision listarEnRevision,
      AcusarRevisionDeGuia acusarGuia,
      AcusarRevisionDeEmision acusarEmision,
      MapeadorBandejaDeRevision mapeador,
      PlatformTransactionManager transactionManager) {
    this.listarEnRevision = Objects.requireNonNull(listarEnRevision);
    this.acusarGuia = Objects.requireNonNull(acusarGuia);
    this.acusarEmision = Objects.requireNonNull(acusarEmision);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping("/revision")
  public BandejaDeRevisionRespuesta listar(
      @RequestParam(defaultValue = "" + MAXIMO_PREDETERMINADO) int maximo) {
    return mapeador.aRespuesta(listarEnRevision.ejecutar(maximo));
  }

  /**
   * Por número de guía y no por identificador interno: es el número el que se teclea, se busca en
   * la página de la transportadora y se pega en un correo.
   */
  @PostMapping("/revision/guias/{numeroGuia}/acuse")
  public AcuseDeRevisionRespuesta acusarGuia(
      @PathVariable String numeroGuia, @RequestBody AcusarRevisionRequest cuerpo) {
    String actor = "admin:" + actorId();
    AcuseDeRevision acuse =
        transaccion.execute(
            estado ->
                acusarGuia.ejecutar(
                    new AcusarRevisionDeGuiaComando(numeroGuia, actor, cuerpo.nota())));
    return mapeador.aRespuesta(acuse);
  }

  @PostMapping("/revision/emisiones/{emisionId}/acuse")
  public AcuseDeRevisionRespuesta acusarEmision(
      @PathVariable UUID emisionId, @RequestBody AcusarRevisionRequest cuerpo) {
    String actor = "admin:" + actorId();
    AcuseDeRevision acuse =
        transaccion.execute(
            estado ->
                acusarEmision.ejecutar(
                    new AcusarRevisionDeEmisionComando(emisionId, actor, cuerpo.nota())));
    return mapeador.aRespuesta(acuse);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
