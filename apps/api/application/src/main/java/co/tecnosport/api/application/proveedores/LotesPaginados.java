package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.List;
import java.util.Objects;

/** Una página de lotes, con la misma forma que {@code ProductosPaginados}. */
public record LotesPaginados(
    List<LoteIngesta> items, int pagina, int totalPaginas, long totalLotes) {

  public LotesPaginados {
    Objects.requireNonNull(items, "Los items no pueden ser nulos.");
    items = List.copyOf(items);
  }
}
