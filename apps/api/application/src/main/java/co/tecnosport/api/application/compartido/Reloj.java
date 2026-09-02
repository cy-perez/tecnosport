package co.tecnosport.api.application.compartido;

import java.time.Instant;

/**
 * Sin este puerto no se prueban vencimientos de reserva ni expiraciones sin dormir el hilo
 * (docs/01-arquitectura.md). Implementación de producción: el reloj del sistema. Implementación de
 * prueba: un instante fijo.
 */
public interface Reloj {

  Instant ahora();
}
