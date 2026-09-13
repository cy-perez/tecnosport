package co.tecnosport.api.application.envio;

import java.util.Optional;

/**
 * Traduce el cuerpo del webhook al vocabulario del dominio.
 *
 * <p>Puerto y no un lector estático —a diferencia del de Wompi, que sí es estático— porque
 * <strong>la forma del evento de Skydropx no está confirmada</strong>: no se ha podido emitir una
 * guía, así que nadie ha visto todavía un evento real. Escribir los nombres de campo de memoria
 * produciría un lector que pasa sus propias pruebas contra un JSON inventado y falla el día del
 * primer despacho.
 *
 * <p>{@link Optional#empty()} significa "esto no lo sé leer", que es una respuesta y no un error:
 * el webhook lo descarta, responde 200 y lo registra.
 */
public interface LectorEventoDeEnvio {

  Optional<AplicarEventoDeEnvioComando> leer(String cuerpoCrudo);
}
