package co.tecnosport.api.domain.catalogo;

/**
 * Si el proveedor todavía lo tiene, hasta donde sabemos. Es un eje aparte de {@link
 * EstadoProducto}: publicar y retirar de la vitrina lo decide una persona; esto lo dicen los
 * mensajes del proveedor y el tiempo. El catálogo público muestra lo que está publicado <b>y</b>
 * disponible.
 */
public enum EstadoDisponibilidad {
  DISPONIBLE,

  /** Lleva más de la ventana sin aparecer en los mensajes. Vuelve solo con el siguiente. */
  OCULTO_POR_VENCIMIENTO,

  /** El proveedor dijo que se acabó. */
  AGOTADO_POR_PROVEEDOR
}
