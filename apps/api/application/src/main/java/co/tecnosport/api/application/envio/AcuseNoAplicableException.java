package co.tecnosport.api.application.envio;

/**
 * Se quiso acusar algo que no está pidiendo revisión. No es un fallo ni una carrera: es que lo que
 * se señaló no estaba en la bandeja.
 *
 * <p>Se rechaza en vez de guardarlo igual porque un acuse sobre algo sano deja escrito que ahí hubo
 * un problema que nunca existió, y eso ensucia justo el rastro que la tabla existe para dar.
 */
public final class AcuseNoAplicableException extends RuntimeException {

  public AcuseNoAplicableException(String porque) {
    super(porque);
  }
}
