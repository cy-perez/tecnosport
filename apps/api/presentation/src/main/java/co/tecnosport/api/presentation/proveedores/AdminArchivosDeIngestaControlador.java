package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.BorrarArchivoDeIngesta;
import co.tecnosport.api.application.proveedores.RepositorioArchivosDeIngesta;
import co.tecnosport.api.presentation.proveedores.dto.ArchivosDeIngestaPaginadosRespuesta;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El historial de los zips subidos para una ingesta, y el borrado de cada uno para la limpieza del
 * bucket privado. Aparte de {@code AdminIngestaControlador}, que es el del trabajo —subir, mirar,
 * pausar, eliminar el lote—; este es el de los archivos. La ruta literal gana a {@code
 * /ingestas/{id}} del vecino.
 */
@RestController
@RequestMapping("/api/v1/admin/ingestas/archivos")
public class AdminArchivosDeIngestaControlador {

  private static final Logger log =
      LoggerFactory.getLogger(AdminArchivosDeIngestaControlador.class);
  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;
  private static final int TAMANO_PAGINA_MAXIMO = 100;

  private final RepositorioArchivosDeIngesta repositorioArchivos;
  private final BorrarArchivoDeIngesta borrarArchivo;
  private final TransactionTemplate transaccion;

  public AdminArchivosDeIngestaControlador(
      RepositorioArchivosDeIngesta repositorioArchivos,
      BorrarArchivoDeIngesta borrarArchivo,
      PlatformTransactionManager transactionManager) {
    this.repositorioArchivos = Objects.requireNonNull(repositorioArchivos);
    this.borrarArchivo = Objects.requireNonNull(borrarArchivo);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public ArchivosDeIngestaPaginadosRespuesta listarArchivos(
      @RequestParam(required = false) UUID proveedorId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    // Acotados aquí: un tamaño cero dividiría por cero al contar páginas, y un desplazamiento
    // negativo lo rechaza Postgres. Ninguno de los dos es un error de quien usa el panel.
    return ArchivosDeIngestaPaginadosRespuesta.de(
        repositorioArchivos.listar(
            proveedorId, Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_PAGINA_MAXIMO)));
  }

  /**
   * Borra el zip del bucket y deja la ingesta entera; ver {@link BorrarArchivoDeIngesta}. Con un
   * lote que lo lee todavía abierto, {@code 409}. Repetirlo no falla.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void borrarArchivo(@PathVariable UUID id) {
    transaccion.executeWithoutResult(estado -> borrarArchivo.ejecutar(id));
    log.info("Archivo de ingesta {} borrado del bucket.", id);
  }
}
