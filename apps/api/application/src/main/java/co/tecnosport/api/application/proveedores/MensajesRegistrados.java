package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import java.util.List;
import java.util.Objects;

/**
 * Lo que dejó {@link RegistrarMensajesDeProveedor}: cuántos leyó, cuántos descartó por no ser del
 * proveedor y cuáles quedaron registrados por primera vez. Los que ya estaban son la resta.
 */
public record MensajesRegistrados(int leidos, int ignorados, List<MensajeProveedor> nuevos) {

  public MensajesRegistrados {
    Objects.requireNonNull(nuevos, "La lista de nuevos no puede ser nula.");
    nuevos = List.copyOf(nuevos);
  }

  public int cuantosNuevos() {
    return nuevos.size();
  }
}
