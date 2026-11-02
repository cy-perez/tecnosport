package co.tecnosport.api.application.catalogo;

import java.util.List;

/** Las tallas con existencia, una lista por línea. */
public record TallasDisponibles(List<OpcionDeTalla> ropa, List<OpcionDeTalla> calzado) {

  public TallasDisponibles {
    ropa = List.copyOf(ropa);
    calzado = List.copyOf(calzado);
  }
}
