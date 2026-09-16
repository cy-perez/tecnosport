package co.tecnosport.api.application.envio;

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
 * el número de guía — y apartar los eventos que no son de un paquete: por la misma suscripción
 * llegan los de órdenes, cotizaciones, tarifas, cargos extra y recolecciones.
 *
 * <p><strong>Apartar no es descartar</strong>, y por eso la respuesta es {@link LecturaDeEvento} y
 * no un {@link java.util.Optional}: un evento de otro tipo es tan normal como uno de paquete, y
 * confundirlo con un cuerpo roto convierte el registro del webhook en ruido justo cuando hay que
 * leerlo. El porqué entero está en {@link LecturaDeEvento}.
 */
public interface LectorEventoDeEnvio {

  LecturaDeEvento leer(String cuerpoCrudo);
}
