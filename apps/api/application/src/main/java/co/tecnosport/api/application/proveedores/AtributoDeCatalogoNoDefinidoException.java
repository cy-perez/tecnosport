package co.tecnosport.api.application.proveedores;

/** El catálogo no tiene el atributo con el que se arman las variantes: hay que crearlo primero. */
public final class AtributoDeCatalogoNoDefinidoException extends RuntimeException {

  public AtributoDeCatalogoNoDefinidoException(String nombre) {
    super(
        "El catálogo no tiene un atributo «"
            + nombre
            + "» y las variantes del borrador lo necesitan. Créalo en el panel antes de aprobar.");
  }
}
