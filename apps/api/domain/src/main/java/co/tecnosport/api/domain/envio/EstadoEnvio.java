package co.tecnosport.api.domain.envio;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

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

  /**
   * ¿La historia de este paquete terminó? De estos cuatro no va a llegar nada más, así que la
   * conciliación deja de preguntar por ellos: sin eso, un paquete entregado hace tres meses se
   * consultaría en cada vuelta para siempre contra un proveedor limitado a dos peticiones por
   * segundo.
   *
   * <p>No coincide con {@link #exigeRevisionManual()} y conviene no confundirlos: comparten {@code
   * CANCELADO} y {@code DESTRUIDO}, pero un paquete entregado terminó sin necesitar a nadie, y uno
   * retenido necesita a alguien y todavía puede moverse.
   */
  public boolean esTerminal() {
    return this == ENTREGADO || this == EN_DEVOLUCION || this == CANCELADO || this == DESTRUIDO;
  }

  /**
   * Los terminales, por nombre, para quien tenga que preguntárselo a la base. Existe para que ese
   * conjunto no se escriba como literales sueltos dentro de una consulta: ahí, renombrar una
   * constante compila, pasa las pruebas y deja el filtro comparando contra un valor que ya no
   * existe.
   */
  public static Set<String> nombresTerminales() {
    return EnumSet.allOf(EstadoEnvio.class).stream()
        .filter(EstadoEnvio::esTerminal)
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
  }
}
