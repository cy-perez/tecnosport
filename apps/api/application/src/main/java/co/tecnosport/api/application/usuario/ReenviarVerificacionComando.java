package co.tecnosport.api.application.usuario;

/**
 * @param correo el de la cuenta cuyo enlace de verificación se quiere otra vez
 */
public record ReenviarVerificacionComando(String correo) {}
