package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;

/** Lo que el panel cambia antes de aprobar. Lo que no venga en el comando queda como estaba. */
public final class EditarBorrador {

  private final RepositorioBorradores repositorioBorradores;

  public EditarBorrador(RepositorioBorradores repositorioBorradores) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
  }

  public BorradorProducto ejecutar(EditarBorradorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorId(comando.borradorId())
            .orElseThrow(() -> new BorradorNoEncontradoException(comando.borradorId()));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    borrador.editar(
        comando.titulo(),
        comando.tipo(),
        comando.precioVentaSugerido(),
        comando.tallas(),
        comando.cantidadTonos(),
        comando.tonosNombrados(),
        comando.material(),
        comando.descripcion(),
        comando.altEn());
    repositorioBorradores.actualizar(borrador);
    return borrador;
  }
}
