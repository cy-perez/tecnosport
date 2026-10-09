package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.ChatDelZip;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.time.Instant;
import java.util.List;
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

  /** El primero de los lotes que deja la exportación; con un solo chat, el único. */
  public LoteIngesta ejecutar(IniciarIngestaComando comando) {
    return ejecutarTodos(comando).getFirst();
  }

  /**
   * Los lotes que deja la exportación, en el orden en que se tienen que procesar. Uno, salvo el de
   * un proveedor que sube sus dos chats en un zip: entonces dos sobre el mismo archivo, el del chat
   * de caballero primero y el general después, para que el general descarte lo que aquel ya trajo
   * (9 de octubre de 2026). El segundo nace un milisegundo después: al reiniciar, los lotes
   * abiertos vuelven a la cola por fecha de creación, y el orden tiene que sobrevivir a eso.
   */
  public List<LoteIngesta> ejecutarTodos(IniciarIngestaComando comando) {
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

    Instant ahora = reloj.ahora();
    if (!proveedor.subeDosChatsEnUnZip()) {
      LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), comando.objectKey(), ahora);
      repositorioLotes.guardar(lote);
      return List.of(lote);
    }
    LoteIngesta deCaballero =
        LoteIngesta.recibirExportacion(proveedor.id(), comando.objectKey(), ahora);
    deCaballero.leerSoloElChat(ChatDelZip.CABALLERO);
    LoteIngesta general =
        LoteIngesta.recibirExportacion(proveedor.id(), comando.objectKey(), ahora.plusMillis(1));
    general.leerSoloElChat(ChatDelZip.GENERAL);
    repositorioLotes.guardar(deCaballero);
    repositorioLotes.guardar(general);
    return List.of(deCaballero, general);
  }
}
