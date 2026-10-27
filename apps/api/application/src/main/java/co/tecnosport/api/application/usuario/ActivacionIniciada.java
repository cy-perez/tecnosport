package co.tecnosport.api.application.usuario;

/**
 * Lo que la persona necesita para dar de alta la cuenta en su aplicación de autenticación: la clave
 * en Base32 —para teclearla— y la URI {@code otpauth://} —para pegarla o abrirla en el teléfono—.
 * Se muestra una vez; si se pierde antes de confirmar, se inicia otra activación.
 */
public record ActivacionIniciada(
    String claveBase32, String uri, int digitos, int periodoSegundos) {}
