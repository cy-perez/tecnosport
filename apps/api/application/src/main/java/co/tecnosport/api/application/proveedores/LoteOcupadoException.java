package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * El lote lo tiene otro trabajador que sigue latiendo. Con Cloud Tasks la tarea responde con error
 * para que la cola la reintente más tarde: si ese trabajador murió, para entonces su latido habrá
 * vencido y el reintento retomará el lote. Sin esto, la tarea se daba por cumplida y el lote
 * dependía de la tarea periódica, que en dev solo corre mientras alguien usa el sitio.
 */
public final class LoteOcupadoException extends RuntimeException {

  public LoteOcupadoException(UUID loteId) {
    super("El lote de ingesta " + loteId + " lo está procesando otro trabajador.");
  }
}
