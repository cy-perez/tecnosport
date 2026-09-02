package co.tecnosport.api.domain.compartido;

/** Base de toda regla de negocio incumplida. Nunca una excepción de framework. */
public class ExcepcionDeDominio extends RuntimeException {

  public ExcepcionDeDominio(String mensaje) {
    super(mensaje);
  }
}
