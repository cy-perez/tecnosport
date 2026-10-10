package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import java.util.Optional;
import java.util.UUID;

/**
 * Los zips subidos para una ingesta. Aparte de {@link RepositorioLotesIngesta} porque el archivo es
 * otra cosa que el trabajo —un zip de dos chats lo leen dos lotes— y porque aquel tiene media
 * docena de dobles de prueba que no tienen por qué enterarse.
 */
public interface RepositorioArchivosDeIngesta {

  /**
   * Inserta, o reescribe la fecha de borrado del que tenga el mismo id. Si ya hay uno con la misma
   * key —el panel avisó dos veces la misma subida, y eso deja dos lotes sobre un objeto— se queda
   * el primero: el archivo es uno solo.
   */
  void guardar(ArchivoDeIngesta archivo);

  Optional<ArchivoDeIngesta> buscarPorId(UUID id);

  /**
   * El historial, del más reciente al más antiguo; el proveedor es opcional. Solo los que algún
   * lote todavía nombra: un zip cuya ingesta se eliminó ya se fue del bucket con ella.
   */
  ArchivosDeIngestaPaginados listar(UUID proveedorId, int pagina, int tamanoPagina);

  /** ¿Algún lote abierto lee este archivo? */
  boolean enUso(String referencia);
}
