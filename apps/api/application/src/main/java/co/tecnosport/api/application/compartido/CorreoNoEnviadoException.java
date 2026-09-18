package co.tecnosport.api.application.compartido;

/**
 * El correo no salió. La lanza el adaptador de {@link EnviadorDeCorreo} y la decide cada caso de
 * uso: unos la atrapan a propósito y otros la dejan subir para reintentar.
 *
 * <p><b>Sin el destinatario en el mensaje</b>, igual que el registro del adaptador: esto termina en
 * un log (docs/08-seguridad-legal.md, registros sin datos personales). La causa original viaja como
 * {@code cause} y ahí sí está el detalle técnico del servidor SMTP, que no es un dato personal.
 */
public final class CorreoNoEnviadoException extends RuntimeException {

  public CorreoNoEnviadoException(Throwable causa) {
    super("No se pudo enviar un correo transaccional.", causa);
  }
}
