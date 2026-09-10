package co.tecnosport.api.application.atencion;

import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import java.time.Instant;

/**
 * La solicitud con su plazo ya resuelto. El límite y el veredicto no son campos del agregado
 * —dependen del calendario y de los plazos configurados, que entran por fuera— pero sin ellos la
 * bandeja no sirve para lo que existe: ver qué está por vencerse.
 */
public record SolicitudConPlazo(
    SolicitudAtencion solicitud, Instant limiteDeRespuesta, VerdictoPlazo verdicto) {}
