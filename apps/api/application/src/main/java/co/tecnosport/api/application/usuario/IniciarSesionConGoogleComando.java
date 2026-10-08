package co.tecnosport.api.application.usuario;

/**
 * @param credencial el ID token que entregó Google al navegador
 * @param autorizaDatos la casilla de «Crear cuenta»: solo hace falta si la cuenta no existe
 */
public record IniciarSesionConGoogleComando(
    String credencial, boolean autorizaDatos, String direccionIp) {}
