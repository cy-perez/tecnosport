package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.IniciarIngesta;
import co.tecnosport.api.application.proveedores.IniciarIngestaComando;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeExportacion;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.presentation.proveedores.dto.IniciarIngestaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.LoteIngestaRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.LotesPaginadosRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.SolicitarSubidaDeExportacionPeticion;
import co.tecnosport.api.presentation.proveedores.dto.SubidaDeExportacionRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La ingesta de una exportación de chat, en tres pasos: pedir la URL firmada, subir el zip directo
 * al bucket, y avisar con la key. El tercero responde {@code 202}: el lote queda en la cola y se
 * procesa aparte; el estado se consulta después.
 *
 * <p><b>Se encola fuera de la transacción</b>, después de que confirme. El trabajador corre en otro
 * hilo y, si arrancara antes, buscaría una fila que todavía no existe.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminIngestaControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminIngestaControlador.class);
  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;

  private final SolicitarSubidaDeExportacion solicitarSubida;
  private final IniciarIngesta iniciarIngesta;
  private final EjecutorDeIngestas ejecutor;
  private final RepositorioLotesIngesta repositorioLotes;
  private final TransactionTemplate transaccion;

  public AdminIngestaControlador(
      SolicitarSubidaDeExportacion solicitarSubida,
      IniciarIngesta iniciarIngesta,
      EjecutorDeIngestas ejecutor,
      RepositorioLotesIngesta repositorioLotes,
      PlatformTransactionManager transactionManager) {
    this.solicitarSubida = Objects.requireNonNull(solicitarSubida);
    this.iniciarIngesta = Objects.requireNonNull(iniciarIngesta);
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/proveedores/{id}/ingestas/url-subida")
  public SubidaDeExportacionRespuesta urlDeSubida(
      @PathVariable UUID id, @RequestBody SolicitarSubidaDeExportacionPeticion cuerpo) {
    SolicitudDeSubida solicitud = solicitarSubida.ejecutar(id, cuerpo.contentType());
    return new SubidaDeExportacionRespuesta(solicitud.url(), solicitud.objectKey());
  }

  @PostMapping("/proveedores/{id}/ingestas")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public LoteIngestaRespuesta iniciar(
      @PathVariable UUID id, @RequestBody IniciarIngestaPeticion cuerpo) {
    LoteIngesta lote =
        transaccion.execute(
            estado -> iniciarIngesta.ejecutar(new IniciarIngestaComando(id, cuerpo.objectKey())));
    ejecutor.encolar(lote.id());
    log.info(
        "Lote de ingesta {} del proveedor {} encolado desde {}", lote.id(), id, cuerpo.objectKey());
    return LoteIngestaRespuesta.de(lote);
  }

  @GetMapping("/ingestas/{id}")
  public LoteIngestaRespuesta ver(@PathVariable UUID id) {
    return repositorioLotes
        .buscarPorId(id)
        .map(LoteIngestaRespuesta::de)
        .orElseThrow(() -> new LoteNoEncontradoException(id));
  }

  @GetMapping("/ingestas")
  public LotesPaginadosRespuesta listar(
      @RequestParam(required = false) UUID proveedorId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    return LotesPaginadosRespuesta.de(repositorioLotes.listar(proveedorId, pagina, tamano));
  }
}
