package co.tecnosport.api.domain.compartido;

/**
 * Una línea pide más unidades de las que se venden por línea ({@link TopeDeUnidadesPorLinea}). El
 * mensaje dice el tope, que es una regla pública, y no cuántas hay: el inventario no se publica
 * (ver {@code ExistenciaInsuficienteException}).
 */
public final class CantidadPorLineaExcedidaException extends ExcepcionDeDominio {

  private final int maximo;

  public CantidadPorLineaExcedidaException(int maximo) {
    super("Puedes llevar hasta " + maximo + " unidades de cada producto.");
    this.maximo = maximo;
  }

  public int maximo() {
    return maximo;
  }
}
