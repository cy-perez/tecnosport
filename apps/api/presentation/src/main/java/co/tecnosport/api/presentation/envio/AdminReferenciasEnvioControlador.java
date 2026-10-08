package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.ConsultarReferenciasDeEnvio;
import co.tecnosport.api.application.envio.FijarMedidasDeReferencia;
import co.tecnosport.api.application.envio.FijarMedidasDeReferenciaComando;
import co.tecnosport.api.application.envio.FijarPesoDeReferencia;
import co.tecnosport.api.application.envio.FijarPesoDeReferenciaComando;
import co.tecnosport.api.application.envio.QuitarPesoDeReferencia;
import co.tecnosport.api.application.envio.ReferenciasDeEnvio;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import co.tecnosport.api.presentation.envio.dto.FijarMedidasDeReferenciaRequest;
import co.tecnosport.api.presentation.envio.dto.FijarPesoDeReferenciaRequest;
import co.tecnosport.api.presentation.envio.dto.MedidasDeReferenciaRespuesta;
import co.tecnosport.api.presentation.envio.dto.PesoDeReferenciaRespuesta;
import co.tecnosport.api.presentation.envio.dto.ReferenciasDeEnvioRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los pesos y las medidas de referencia del envío ({@code adr/0071}), bajo {@code /api/v1/admin/**}
 * y protegidos por rol ADMIN en {@code ConfiguracionSeguridad}.
 *
 * <p>Controlador propio y no un método más de {@link AdminEnviosControlador}: aquella es una
 * bandeja de lo que pide ojo humano y esta es configuración del negocio. Comparten prefijo porque
 * las dos son del envío, no porque se usen juntas.
 *
 * <p>{@code PUT} y no {@code POST}: fijar dos veces el mismo peso deja lo mismo, y eso es lo que
 * {@code PUT} promete. Sin cabecera de idempotencia por eso mismo.
 */
@RestController
@RequestMapping("/api/v1/admin/envios/referencias")
public class AdminReferenciasEnvioControlador {

  private final ConsultarReferenciasDeEnvio consultar;
  private final FijarMedidasDeReferencia fijarMedidas;
  private final FijarPesoDeReferencia fijarPeso;
  private final QuitarPesoDeReferencia quitarPeso;
  private final TransactionTemplate transaccion;

  public AdminReferenciasEnvioControlador(
      ConsultarReferenciasDeEnvio consultar,
      FijarMedidasDeReferencia fijarMedidas,
      FijarPesoDeReferencia fijarPeso,
      QuitarPesoDeReferencia quitarPeso,
      PlatformTransactionManager transactionManager) {
    this.consultar = Objects.requireNonNull(consultar);
    this.fijarMedidas = Objects.requireNonNull(fijarMedidas);
    this.fijarPeso = Objects.requireNonNull(fijarPeso);
    this.quitarPeso = Objects.requireNonNull(quitarPeso);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public ReferenciasDeEnvioRespuesta consultar() {
    ReferenciasDeEnvio referencias = consultar.ejecutar();
    return new ReferenciasDeEnvioRespuesta(
        referencias.medidas().map(AdminReferenciasEnvioControlador::aRespuesta).orElse(null),
        referencias.categorias().stream()
            .map(
                fila ->
                    new ReferenciasDeEnvioRespuesta.CategoriaConPesoRespuesta(
                        fila.categoria().id().toString(),
                        fila.categoria().nombre(),
                        fila.rama(),
                        fila.categoria().linea().name(),
                        fila.pesoGramos()))
            .toList());
  }

  @PutMapping("/medidas")
  public MedidasDeReferenciaRespuesta fijarMedidas(
      @RequestBody FijarMedidasDeReferenciaRequest cuerpo) {
    MedidasDeReferencia medidas =
        transaccion.execute(
            estado ->
                fijarMedidas.ejecutar(
                    new FijarMedidasDeReferenciaComando(
                        cuerpo.largoCm(), cuerpo.anchoCm(), cuerpo.altoCm())));
    return aRespuesta(medidas);
  }

  @PutMapping("/pesos/{categoriaId}")
  public PesoDeReferenciaRespuesta fijarPeso(
      @PathVariable UUID categoriaId, @RequestBody FijarPesoDeReferenciaRequest cuerpo) {
    PesoDeReferencia peso =
        transaccion.execute(
            estado ->
                fijarPeso.ejecutar(
                    new FijarPesoDeReferenciaComando(categoriaId, cuerpo.pesoGramos())));
    return new PesoDeReferenciaRespuesta(peso.categoriaId().toString(), peso.pesoGramos());
  }

  @DeleteMapping("/pesos/{categoriaId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void quitarPeso(@PathVariable UUID categoriaId) {
    transaccion.executeWithoutResult(estado -> quitarPeso.ejecutar(categoriaId));
  }

  private static MedidasDeReferenciaRespuesta aRespuesta(MedidasDeReferencia medidas) {
    return new MedidasDeReferenciaRespuesta(medidas.largoCm(), medidas.anchoCm(), medidas.altoCm());
  }
}
