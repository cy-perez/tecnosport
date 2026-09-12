package co.tecnosport.api.application.envio;

/**
 * ¿Este evento lo mandó Skydropx? Puerto y no una función suelta porque la respuesta depende de un
 * secreto que vive en la configuración y de un algoritmo que <strong>todavía no está
 * confirmado</strong>.
 *
 * <p>La única pista que hay es de una fuente no oficial: cabecera {@code authorization} con el
 * formato {@code HMAC {firma}} y HMAC SHA-512 sobre el cuerpo. Coincide con lo que anotaba
 * adr/0022, pero una sola fuente no basta — es el mismo error que ya costó una sesión con el vector
 * de firma de Wompi. Hasta confirmarlo contra un evento real, la implementación de producción
 * rechaza todo.
 *
 * <p>El cuerpo llega <strong>crudo</strong>, como cadena, y no como objeto ya parseado: un HMAC se
 * calcula sobre los bytes que llegaron. Volver a serializar un JSON reordena claves y cambia
 * espacios, y la firma deja de cuadrar por un motivo que nadie encuentra mirando el código.
 */
public interface VerificadorFirmaEnvio {

  boolean esValida(String cuerpoCrudo, String firma);
}
