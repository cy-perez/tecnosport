package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra del bucket privado el zip de una ingesta y deja todo lo demás: los lotes con su resumen,
 * los mensajes, los borradores y las fotos, que viven en objetos propios. Es la limpieza periódica
 * del bucket, no deshacer la ingesta —eso es {@link EliminarLoteDeIngesta}—.
 *
 * <p><b>Nada vuelve a leer el zip</b> una vez que sus lotes cerraron: las fotos se leen de sus
 * propias keys, y rehacer una ingesta es eliminarla y volver a subir la exportación. Por eso la
 * única guarda es que ningún lote que lo lea siga abierto ({@link ArchivoDeIngestaEnUsoException}).
 *
 * <p>Repetirlo no falla: un archivo ya borrado se queda como estaba, con la fecha de la primera
 * vez. El objeto se borra antes de marcar la fila, por la razón de siempre: al revés, un fallo a
 * mitad dejaría marcado como borrado un archivo que sigue ocupando el bucket.
 */
public final class BorrarArchivoDeIngesta {

  private final RepositorioArchivosDeIngesta repositorioArchivos;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final Reloj reloj;

  public BorrarArchivoDeIngesta(
      RepositorioArchivosDeIngesta repositorioArchivos,
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj) {
    this.repositorioArchivos = Objects.requireNonNull(repositorioArchivos);
    this.almacen = Objects.requireNonNull(almacen);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ArchivoDeIngesta ejecutar(UUID archivoId) {
    Objects.requireNonNull(archivoId, "El id no puede ser nulo.");
    ArchivoDeIngesta archivo =
        repositorioArchivos
            .buscarPorId(archivoId)
            .orElseThrow(() -> new ArchivoDeIngestaNoEncontradoException(archivoId));
    if (archivo.borrado()) {
      return archivo;
    }
    if (repositorioArchivos.enUso(archivo.referencia())) {
      throw new ArchivoDeIngestaEnUsoException();
    }
    almacen.borrar(archivo.referencia());
    archivo.marcarBorrado(reloj.ahora());
    repositorioArchivos.guardar(archivo);
    return archivo;
  }
}
