package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * La cola en la que un lote espera a que alguien procese su siguiente tramo.
 *
 * <p>El endpoint responde {@code 202} en cuanto el lote queda escrito, y esto es lo que lo hace
 * avanzar después. Quien encola lo hace <b>después de confirmar la transacción</b> que escribió el
 * lote: el trabajador corre en otro hilo —u otra instancia— y, si arranca antes, no encuentra la
 * fila como quedó.
 *
 * <p>Dos implementaciones (ADR-0080): Cloud Tasks en los ambientes desplegados, donde cada tramo es
 * una petición y la cola vive fuera de la aplicación, y un hilo con su cola en memoria en local y
 * en las pruebas. Encolar dos veces el mismo lote no hace daño: el tramo que llegue segundo lo
 * encuentra tomado, o ya cerrado, y no hace nada.
 */
public interface EjecutorDeIngestas {

  /**
   * @throws ColaDeIngestasLlenaException si la cola en memoria no admite uno más
   * @throws ColaDeIngestasNoDisponibleException si la cola de afuera no respondió
   */
  void encolar(UUID loteId);

  /**
   * Si lo encolado sobrevive a un reinicio de la aplicación. La cola en memoria no: lo que estaba
   * en la cola al apagarse hay que volver a encolarlo al arrancar ({@link ReanudarLotesDeIngesta}).
   * La de Cloud Tasks sí, y volver a encolarlo en cada arranque —que en dev son muchos— solo
   * duplicaría tareas.
   */
  boolean sobreviveAUnReinicio();
}
