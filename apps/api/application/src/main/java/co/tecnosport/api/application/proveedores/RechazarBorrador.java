package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;
import java.util.UUID;

/** No se publica, y queda dicho por qué. El borrador no se borra: es la constancia. */
public final class RechazarBorrador {

  private final RepositorioBorradores repositorioBorradores;

  public RechazarBorrador(RepositorioBorradores repositorioBorradores) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
  }

  public BorradorProducto ejecutar(UUID borradorId, String motivo) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    borrador.rechazar(motivo);
    repositorioBorradores.actualizar(borrador);
    return borrador;
  }
}
