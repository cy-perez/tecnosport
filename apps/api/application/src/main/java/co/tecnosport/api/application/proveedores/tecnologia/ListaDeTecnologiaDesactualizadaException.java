package co.tecnosport.api.application.proveedores.tecnologia;

import java.time.LocalDate;

/** La lista es más vieja que la última que entró: aplicarla deshacería la de después. */
public final class ListaDeTecnologiaDesactualizadaException extends RuntimeException {
  public ListaDeTecnologiaDesactualizadaException(LocalDate fechaLista, LocalDate ultima) {
    super(
        "La lista del "
            + fechaLista
            + " es más vieja que la última que entró, del "
            + ultima
            + ". Importa una lista de esa fecha o posterior.");
  }
}
