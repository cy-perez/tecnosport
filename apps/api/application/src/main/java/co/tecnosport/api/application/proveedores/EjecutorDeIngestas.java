package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * La cola en la que un lote espera a que alguien lo procese.
 *
 * <p>El endpoint responde {@code 202} en cuanto el lote queda escrito, y esto es lo que lo hace
 * avanzar después. Quien encola lo hace <b>después de confirmar la transacción</b> que escribió el
 * lote: el trabajador corre en otro hilo y, si arranca antes, no encuentra la fila.
 */
public interface EjecutorDeIngestas {

  /**
   * @throws ColaDeIngestasLlenaException si no cabe uno más
   */
  void encolar(UUID loteId);
}
