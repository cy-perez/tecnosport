package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.AcusarRevisionDeEmision;
import co.tecnosport.api.application.envio.AcusarRevisionDeEmisionComando;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuia;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuiaComando;
import co.tecnosport.api.application.envio.ListarEnviosEnRevision;
import co.tecnosport.api.application.envio.ResolverEmisionIndeterminada;
import co.tecnosport.api.application.envio.ResolverEmisionIndeterminadaComando;
import co.tecnosport.api.application.envio.VeredictoDeEmision;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.presentation.envio.dto.AcusarRevisionRequest;
import co.tecnosport.api.presentation.envio.dto.AcuseDeRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.BandejaDeRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.EmisionResueltaRespuesta;
import co.tecnosport.api.presentation.envio.dto.ResolverEmisionRequest;
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
  private final ResolverEmisionIndeterminada resolverEmision;
  private final MapeadorBandejaDeRevision mapeador;
  private final TransactionTemplate transaccion;

  public AdminEnviosControlador(
      ListarEnviosEnRevision listarEnRevision,
      AcusarRevisionDeGuia acusarGuia,
      AcusarRevisionDeEmision acusarEmision,
      ResolverEmisionIndeterminada resolverEmision,
      MapeadorBandejaDeRevision mapeador,
      PlatformTransactionManager transactionManager) {
    this.listarEnRevision = Objects.requireNonNull(listarEnRevision);
    this.acusarGuia = Objects.requireNonNull(acusarGuia);
    this.acusarEmision = Objects.requireNonNull(acusarEmision);
    this.resolverEmision = Objects.requireNonNull(resolverEmision);
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

  /**
   * Resolver es distinto de acusar y por eso es otra ruta: acusar deja constancia, resolver
   * <strong>desbloquea el pedido</strong>. Solo aplica a una emisión {@code INDETERMINADA}, que es
   * la única de la que se puede decir que se cerró sin saber.
   */
  @PostMapping("/revision/emisiones/{emisionId}/resolucion")
  public EmisionResueltaRespuesta resolverEmision(
      @PathVariable UUID emisionId, @RequestBody ResolverEmisionRequest cuerpo) {
    String actor = "admin:" + actorId();
    VeredictoDeEmision veredicto = veredictoDe(cuerpo.veredicto());
    EmisionDeGuia emision =
        transaccion.execute(
            estado ->
                resolverEmision.ejecutar(
                    new ResolverEmisionIndeterminadaComando(
                        emisionId, veredicto, cuerpo.enviosEnPlataforma(), actor, cuerpo.nota())));
    return mapeador.aRespuesta(emision);
  }

  /**
   * {@code valueOf} directo como en el resto del panel —{@code IllegalArgumentException} ya sale
   * como 422— pero con el nulo cubierto: un cuerpo sin {@code veredicto} llegaría a {@code valueOf}
   * como {@code null} y reventaría en un 500 en vez de decir qué falta.
   */
  private static VeredictoDeEmision veredictoDe(String valor) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException("El veredicto es obligatorio: SIN_COBRO o CON_ENVIO.");
    }
    return VeredictoDeEmision.valueOf(valor);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
