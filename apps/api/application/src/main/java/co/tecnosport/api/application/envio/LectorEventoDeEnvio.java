package co.tecnosport.api.application.envio;

import java.util.Optional;

/**
 * Saca del cuerpo del webhook <strong>de qué guía habla</strong>, y nada más.
 *
 * <p>Que devuelva la guía y no el evento es la decisión de adr/0032, y no es una simplificación: el
 * cuerpo del webhook <em>no trae con qué construir un evento</em>. Trae {@code status}, {@code
 * tracking_number}, la URL del rótulo y el estado de retorno —está documentado y medido—, pero
 * ningún identificador de evento y ninguna fecha. El identificador es lo que hace idempotente el
 * rastro y de la fecha cuelgan plazos legales; fabricar cualquiera de los dos habría metido el
 * mismo movimiento dos veces, una por cada camino, con llaves distintas.
 *
 * <p>Así que el webhook avisa y {@link ConciliarGuia} pregunta. Lo único que hay que leer aquí es
 * el número de guía — y descartar los eventos que no son de un paquete: por la misma suscripción
 * llegan los de órdenes, cotizaciones, tarifas, cargos extra y recolecciones.
 *
 * <p>{@link Optional#empty()} significa "esto no lo sé leer", que es una respuesta y no un error:
 * el webhook lo descarta, responde 200 y lo registra.
 */
public interface LectorEventoDeEnvio {

  Optional<String> guiaDelEvento(String cuerpoCrudo);
}
