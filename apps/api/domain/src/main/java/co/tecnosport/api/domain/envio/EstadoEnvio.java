package co.tecnosport.api.domain.envio;

/**
 * Los doce estados con los que Skydropx describe el movimiento de un paquete (adr/0022). Son de la
 * plataforma, no nuestros, y por eso viven en su propio enum en vez de mezclarse con {@code
 * EstadoPedido}: duplicar ahí la máquina de estados de la transportadora acoplaría el grafo del
 * pedido al vocabulario de un proveedor, y ese grafo es la parte del dominio que más caro sale
 * mover.
 *
 * <p>Solo tres mueven el pedido —{@link #RECOGIDO}, {@link #ENTREGADO} y {@link #EN_DEVOLUCION}—.
 * Los demás se registran y ya. Cuatro de ellos piden ojo humano: son los casos en que el paquete se
 * queda quieto y nadie lo nota hasta que reclama el comprador.
 */
public enum EstadoEnvio {
  CREADO,
  RECOGIDO,
  EN_TRANSITO,
  ULTIMA_MILLA,
  INTENTO_DE_ENTREGA,
  ENTREGADO_EN_OFICINA,
  ENTREGADO,
  EXCEPCION,
  EN_DEVOLUCION,
  CANCELADO,
  DESTRUIDO,
  RETENIDO;

  /**
   * ¿Este estado deja el paquete quieto sin que nadie se entere? {@code EXCEPCION}, {@code
   * RETENIDO}, {@code CANCELADO} y {@code DESTRUIDO} no mueven el pedido y tampoco lo hacen
   * avanzar: si nadie los mira, el comprador se entera antes que el negocio (adr/0022).
   */
  public boolean exigeRevisionManual() {
    return this == EXCEPCION || this == RETENIDO || this == CANCELADO || this == DESTRUIDO;
  }
}
