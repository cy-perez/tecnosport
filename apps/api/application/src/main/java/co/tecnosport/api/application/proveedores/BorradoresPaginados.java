package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import java.util.List;
import java.util.Objects;

public record BorradoresPaginados(
    List<BorradorProducto> items, int pagina, int totalPaginas, long totalBorradores) {

  public BorradoresPaginados {
    Objects.requireNonNull(items, "Los items no pueden ser nulos.");
    items = List.copyOf(items);
  }
}
