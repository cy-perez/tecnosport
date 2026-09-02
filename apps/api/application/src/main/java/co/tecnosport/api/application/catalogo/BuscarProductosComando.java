package co.tecnosport.api.application.catalogo;

import java.util.Objects;

public record BuscarProductosComando(
    FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {

  public static final int TAMANO_PAGINA_MAXIMO = 60;

  public BuscarProductosComando {
    Objects.requireNonNull(filtro, "El filtro no puede ser nulo; use FiltroProductos.vacio().");
    Objects.requireNonNull(orden, "El orden no puede ser nulo.");
    if (tamanoPagina < 1 || tamanoPagina > TAMANO_PAGINA_MAXIMO) {
      throw new IllegalArgumentException(
          "El tamaño de página debe estar entre 1 y " + TAMANO_PAGINA_MAXIMO + ": " + tamanoPagina);
    }
  }
}
