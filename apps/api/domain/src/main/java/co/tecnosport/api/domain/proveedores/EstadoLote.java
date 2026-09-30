package co.tecnosport.api.domain.proveedores;

/**
 * En qué punto está un lote de ingesta.
 *
 * <p>{@code RECIBIDO} y {@code PROCESANDO} son dos estados y no uno porque entre aceptar el archivo
 * y empezar a leerlo hay una cola: la petición responde en cuanto el lote queda escrito y el
 * trabajo lo hace otro hilo. Un lote que lleva mucho en {@code RECIBIDO} dice que la cola no
 * avanza; uno que lleva mucho en {@code PROCESANDO} dice que el trabajo se cayó a medias. Son dos
 * diagnósticos distintos.
 */
public enum EstadoLote {
  /** El archivo está en el almacén y el lote en la cola. */
  RECIBIDO,

  /** Alguien lo está leyendo. */
  PROCESANDO,

  /** Se leyó entero y el resumen dice qué salió de ahí. */
  TERMINADO,

  /** No se pudo terminar, y el detalle dice por qué. */
  ERROR
}
