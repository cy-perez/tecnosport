package co.tecnosport.api.application.usuario;

/**
 * {@code autorizaDatos} y {@code direccionIp} son para la constancia de tratamiento de datos (Ley
 * 1581 de 2012). La versión del texto no viaja aquí a propósito: la fija el servidor, porque un
 * cliente que declarara qué versión aceptó podría dejar constancia de una aceptación que nunca
 * ocurrió (regla dura #7).
 */
public record RegistrarUsuarioComando(
    String correo, String claveTextoPlano, boolean autorizaDatos, String direccionIp) {}
