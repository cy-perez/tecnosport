package co.tecnosport.api.application.envio;

/**
 * ¿Este evento lo mandó Skydropx? Puerto y no una función suelta porque la respuesta depende de un
 * secreto que vive en la configuración.
 *
 * <p>El algoritmo quedó confirmado en la documentación oficial el 14 de septiembre de 2026
 * —cabecera con {@code HMAC <firma>}, HMAC-SHA512 sobre los bytes crudos, hexadecimal en
 * minúsculas— y lo implementa {@code VerificadorFirmaEnvioHmac}. Hasta entonces la implementación
 * de producción rechazaba todo: el Javadoc de este puerto decía que el algoritmo "todavía no está
 * confirmado" y se apoyaba en una sola fuente no oficial, que es el mismo error que ya costó una
 * sesión con el vector de firma de Wompi.
 *
 * <p>El cuerpo llega <strong>crudo</strong>, como cadena, y no como objeto ya parseado: un HMAC se
 * calcula sobre los bytes que llegaron. Volver a serializar un JSON reordena claves y cambia
 * espacios, y la firma deja de cuadrar por un motivo que nadie encuentra mirando el código. Quien
 * llama es responsable de que esa cadena venga de decodificar los bytes de la petición en UTF-8 —lo
 * hace {@code EnvioWebhookControlador}, que por eso recibe {@code byte[]}—, porque la ida y la
 * vuelta solo son exactas con una codificación fijada.
 */
public interface VerificadorFirmaEnvio {

  boolean esValida(String cuerpoCrudo, String firma);
}
