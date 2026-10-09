package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Objects;

/**
 * El zip ya está en el bucket; aquí nace el lote.
 *
 * <p>Se comprueba lo que se puede comprobar sin abrir el archivo: que la key sea de una exportación
 * de <em>ese</em> proveedor —una key ajena no entra aunque exista—, que el objeto esté y que no
 * pase del tope. Abrirlo es trabajo del procesamiento, que corre aparte.
 *
 * <p><b>No encola.</b> La transacción la abre el controlador, y encolar dentro de ella haría que el
 * trabajador pudiera arrancar antes de que la fila del lote esté confirmada. Encola el controlador,
 * al salir de la transacción.
 */
public final class IniciarIngesta {

  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioLotesIngesta repositorioLotes;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final Reloj reloj;
  private final long maximoBytes;

  public IniciarIngesta(
      RepositorioProveedores repositorioProveedores,
      RepositorioLotesIngesta repositorioLotes,
      AlmacenDeArchivosDeProveedor almacen,
      Reloj reloj,
      long maximoBytes) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.almacen = Objects.requireNonNull(almacen);
    this.reloj = Objects.requireNonNull(reloj);
    if (maximoBytes <= 0) {
      throw new IllegalArgumentException("El tamaño máximo de una exportación es positivo.");
    }
    this.maximoBytes = maximoBytes;
  }

  public LoteIngesta ejecutar(IniciarIngestaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(comando.proveedorId())
            .orElseThrow(() -> new ProveedorNoEncontradoException(comando.proveedorId()));
    if (!proveedor.activo()) {
      throw new ProveedorInactivoException(proveedor.nombre());
    }
    if (!proveedor.entraPorExportacion()) {
      throw new ProveedorDeListasException(proveedor.nombre());
    }
    if (!ClavesDeProveedor.esExportacionDe(proveedor.id(), comando.objectKey())) {
      throw new ExportacionNoEncontradaException(comando.objectKey());
    }
    long tamano =
        almacen
            .tamanoBytes(comando.objectKey())
            .filter(bytes -> bytes > 0)
            .orElseThrow(() -> new ExportacionNoEncontradaException(comando.objectKey()));
    if (tamano > maximoBytes) {
      throw new ExportacionDemasiadoGrandeException(tamano, maximoBytes);
    }

    LoteIngesta lote =
        LoteIngesta.recibirExportacion(proveedor.id(), comando.objectKey(), reloj.ahora());
    repositorioLotes.guardar(lote);
    return lote;
  }
}
