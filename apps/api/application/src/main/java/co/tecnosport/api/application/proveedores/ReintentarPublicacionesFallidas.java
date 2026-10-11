package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vuelve a intentar las publicaciones de un lote terminado que quedaron en {@code ERROR}: vuelven a
 * esperar al extractor y el lote vuelve a la cola. <b>Quien llama lo encola</b> después de
 * confirmar ({@link EncolarIngesta}), como al reanudar.
 *
 * <p>Existe por la ingesta de Meraki del 11 de octubre de 2026: sus 27 publicaciones fallaron por
 * la cuenta del modelo, y volver a subir el zip no servía —los mensajes ya estaban registrados y no
 * se vuelven a leer—. La única salida era eliminar el lote y subirlo otra vez.
 */
public final class ReintentarPublicacionesFallidas {

  private final RepositorioLotesIngesta repositorioLotes;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final Reloj reloj;

  public ReintentarPublicacionesFallidas(
      RepositorioLotesIngesta repositorioLotes,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /**
   * @return el lote, de vuelta en la cola
   * @throws LoteNoEncontradoException si no existe
   * @throws LoteEnOtroEstadoException si no está terminado o no tiene fallidas
   * @throws OtraIngestaDelProveedorEnCursoException si otro lote del proveedor sigue abierto
   */
  public LoteIngesta ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorIdParaActualizar(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    boolean otroAbierto =
        repositorioLotes.abiertos().stream()
            .anyMatch(
                otro -> otro.proveedorId().equals(lote.proveedorId()) && !otro.id().equals(loteId));
    if (otroAbierto) {
      throw new OtraIngestaDelProveedorEnCursoException();
    }
    List<PublicacionProveedor> fallidas =
        repositorioPublicaciones.listarDeLote(loteId).stream()
            .filter(p -> p.estado() == EstadoPublicacionProveedor.ERROR)
            .toList();
    try {
      lote.reabrirFallidas(reloj.ahora());
    } catch (ExcepcionDeDominio e) {
      throw new LoteEnOtroEstadoException(lote.estado());
    }
    fallidas.forEach(
        publicacion -> {
          publicacion.reabrir();
          repositorioPublicaciones.actualizar(publicacion);
        });
    repositorioLotes.actualizar(lote);
    return lote;
  }
}
