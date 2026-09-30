package co.tecnosport.api.application.proveedores;

/** Ni el extractor dio título ni la aprobación lo trae. Sin nombre no hay producto. */
public final class BorradorSinTituloException extends RuntimeException {

  public BorradorSinTituloException() {
    super("El borrador no tiene título. Escríbelo en la aprobación.");
  }
}
