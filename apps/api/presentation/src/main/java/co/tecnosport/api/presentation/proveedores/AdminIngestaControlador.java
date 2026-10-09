package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.proveedores.DetenerIngesta;
import co.tecnosport.api.application.proveedores.EliminarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.EncolarIngesta;
import co.tecnosport.api.application.proveedores.IniciarIngesta;
import co.tecnosport.api.application.proveedores.IniciarIngestaComando;
import co.tecnosport.api.application.proveedores.LoteEliminado;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.PausarIngesta;
import co.tecnosport.api.application.proveedores.ReanudarIngesta;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeExportacion;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.presentation.proveedores.dto.IniciarIngestaPeticion;
import co.tecnosport.api.presentation.proveedores.dto.LoteEliminadoRespuesta;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
  private final EncolarIngesta encolarIngesta;
  private final RepositorioLotesIngesta repositorioLotes;
  private final TransactionTemplate transaccion;
  private final EliminarLoteDeIngesta eliminarLote;
  private final PausarIngesta pausarIngesta;
  private final ReanudarIngesta reanudarIngesta;
  private final DetenerIngesta detenerIngesta;

  public AdminIngestaControlador(
      SolicitarSubidaDeExportacion solicitarSubida,
      IniciarIngesta iniciarIngesta,
      EncolarIngesta encolarIngesta,
      RepositorioLotesIngesta repositorioLotes,
      PlatformTransactionManager transactionManager,
      EliminarLoteDeIngesta eliminarLote,
      PausarIngesta pausarIngesta,
      ReanudarIngesta reanudarIngesta,
      DetenerIngesta detenerIngesta) {
    this.solicitarSubida = Objects.requireNonNull(solicitarSubida);
    this.iniciarIngesta = Objects.requireNonNull(iniciarIngesta);
    this.encolarIngesta = Objects.requireNonNull(encolarIngesta);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.eliminarLote = Objects.requireNonNull(eliminarLote);
    this.pausarIngesta = Objects.requireNonNull(pausarIngesta);
    this.reanudarIngesta = Objects.requireNonNull(reanudarIngesta);
    this.detenerIngesta = Objects.requireNonNull(detenerIngesta);
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
    // Si la cola está llena, EncolarIngesta cierra el lote con su motivo y relanza (409).
    encolarIngesta.ejecutar(lote);
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

  /**
   * Borra la ingesta con sus borradores y los productos no publicados que salieron de ella; ver
   * {@link EliminarLoteDeIngesta}. {@code 200} y no {@code 204}: el panel dice cuántos productos se
   * fueron y cuántos se quedaron por estar publicados. En curso, {@code 409}.
   */
  @DeleteMapping("/ingestas/{id}")
  public LoteEliminadoRespuesta eliminar(@PathVariable UUID id) {
    LoteEliminado resultado = transaccion.execute(estado -> eliminarLote.ejecutar(id));
    log.info(
        "Lote de ingesta {} eliminado: {} productos borrados, {} conservados, {} archivos.",
        id,
        resultado.productosEliminados(),
        resultado.productosConservados(),
        resultado.archivosBorrados());
    return new LoteEliminadoRespuesta(
        resultado.productosEliminados(), resultado.productosConservados());
  }

  /**
   * El trabajador espera entre una publicación y otra hasta que se reanude o se detenga, y la cola
   * espera con él. Solo un lote en proceso; si ya no lo está —terminó mientras se pulsaba—, {@code
   * 422} con el estado en que quedó.
   */
  @PostMapping("/ingestas/{id}/pausar")
  public LoteIngestaRespuesta pausar(@PathVariable UUID id) {
    LoteIngesta lote = transaccion.execute(estado -> pausarIngesta.ejecutar(id));
    log.info("Lote de ingesta {} en pausa.", id);
    return LoteIngestaRespuesta.de(lote);
  }

  @PostMapping("/ingestas/{id}/reanudar")
  public LoteIngestaRespuesta reanudar(@PathVariable UUID id) {
    LoteIngesta lote = transaccion.execute(estado -> reanudarIngesta.ejecutar(id));
    log.info("Lote de ingesta {} reanudado.", id);
    return LoteIngestaRespuesta.de(lote);
  }

  /**
   * En la cola, el lote queda {@code DETENIDO} en el acto; en curso o en pausa, {@code DETENIENDO}
   * hasta que el trabajador lo suelte y siga con el siguiente. Ver {@link DetenerIngesta}.
   */
  @PostMapping("/ingestas/{id}/detener")
  public LoteIngestaRespuesta detener(@PathVariable UUID id) {
    LoteIngesta lote = transaccion.execute(estado -> detenerIngesta.ejecutar(id));
    log.info("Lote de ingesta {}: detención pedida, queda {}.", id, lote.estado());
    return LoteIngestaRespuesta.de(lote);
  }

  @GetMapping("/ingestas")
  public LotesPaginadosRespuesta listar(
      @RequestParam(required = false) UUID proveedorId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    return LotesPaginadosRespuesta.de(repositorioLotes.listar(proveedorId, pagina, tamano));
  }
}
