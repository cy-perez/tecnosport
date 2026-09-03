package co.tecnosport.api.application.pago;

/**
 * {@code sinNovedad} agrupa todo lo que no cambió nada: la transacción sigue pendiente en Wompi, la
 * consulta falló (red, id inexistente — se reintenta en la próxima corrida), o el evento ya se
 * había aplicado antes (por ejemplo, el webhook llegó justo antes que esta corrida).
 */
public record ResultadoConciliacion(int revisados, int conciliados, int sinNovedad) {}
