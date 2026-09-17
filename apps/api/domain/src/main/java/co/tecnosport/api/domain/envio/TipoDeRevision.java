package co.tecnosport.api.domain.envio;

/**
 * Qué se revisó: una guía que dejó de moverse o una emisión con plata comprometida.
 *
 * <p>Son dos cosas distintas y la bandeja las muestra juntas porque para quien atiende es una sola
 * pregunta —"¿qué paquete necesita que alguien haga algo?"—, pero el acuse guarda cuál de las dos
 * era. Sin este campo, una referencia suelta no se sabe contra qué tabla resolver, y el día que un
 * identificador de guía coincidiera con uno de emisión el acuse taparía la fila equivocada.
 */
public enum TipoDeRevision {

  /** Una {@link GuiaEnvio} cuyo último evento la dejó quieta: excepción, retención, cancelación. */
  GUIA,

  /**
   * Una {@link EmisionDeGuia} en {@link EstadoEmision#INDETERMINADA} o {@link
   * EstadoEmision#PARCIAL}: hay saldo comprometido y solo una persona puede decidir qué pasó.
   */
  EMISION
}
