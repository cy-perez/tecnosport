package co.tecnosport.api.domain.envio;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los trece estados con los que Skydropx describe el movimiento de un paquete (adr/0022). Son de la
 * plataforma, no nuestros, y por eso viven en su propio enum en vez de mezclarse con {@code
 * EstadoPedido}: duplicar ahí la máquina de estados de la transportadora acoplaría el grafo del
 * pedido al vocabulario de un proveedor, y ese grafo es la parte del dominio que más caro sale
 * mover.
 *
 * <p>Eran doce hasta el 16 de septiembre de 2026, que son los del enum del <em>rastreo</em>. El
 * canal del webhook tiene su propio vocabulario y trae uno más, {@link #FALLIDO} — medido en el
 * cuerpo de un evento de prueba del panel, con su número de guía (docs/13 §6.9). Son dos listas y
 * sólo se había mirado una.
 *
 * <p>Solo tres mueven el pedido —{@link #RECOGIDO}, {@link #ENTREGADO} y {@link #EN_DEVOLUCION}—.
 * Los demás se registran y ya. Cinco de ellos piden ojo humano: son los casos en que el paquete se
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
  RETENIDO,

  /**
   * El {@code error} de la plataforma. Se llama así y no {@code ERROR} porque los nombres de este
   * enum son nuestros —la tabla del mapeador traduce explícitamente— y "error" en un dominio se lee
   * como una excepción en vez de como lo que es: un paquete que no va a moverse.
   *
   * <p><strong>Lo que significa exactamente no está medido.</strong> Que sea el aviso del {@code
   * workflow_status: error} de docs/13 §6.6 —la guía que se emite, muere minutos después y se
   * reembolsa— es plausible y no está comprobado; se confirma el día que una emisión real vuelva a
   * morir. Por eso entra pidiendo ojo humano y <strong>no</strong> como terminal: ver {@link
   * #esTerminal()}.
   */
  FALLIDO;

  /**
   * ¿Este estado deja el paquete quieto sin que nadie se entere? {@code EXCEPCION}, {@code
   * RETENIDO}, {@code CANCELADO}, {@code DESTRUIDO} y {@code FALLIDO} no mueven el pedido y tampoco
   * lo hacen avanzar: si nadie los mira, el comprador se entera antes que el negocio (adr/0022).
   *
   * <p><strong>Y a día de hoy nadie los mira</strong>: este predicado no lo llama nada en
   * producción, sólo las pruebas. La pregunta está bien planteada y no tiene quien la haga —el
   * panel no marca estos envíos y la conciliación no los separa—. Queda anotado aquí porque un
   * método que sólo se prueba a sí mismo parece cubierto y no cubre nada.
   */
  public boolean exigeRevisionManual() {
    return this == EXCEPCION
        || this == RETENIDO
        || this == CANCELADO
        || this == DESTRUIDO
        || this == FALLIDO;
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
   *
   * <p><strong>{@link #FALLIDO} no está aquí a propósito.</strong> Parece terminal —una guía muerta
   * no se mueve— pero eso es justo lo que no se ha medido: sabemos que el estado existe, no que sea
   * final. Darlo por terminado haría que la conciliación dejara de preguntar por ese envío para
   * siempre, y si el estado resultara transitorio nos quedaríamos ciegos sin enterarnos. Seguir
   * preguntando cuesta una llamada por vuelta, acotada por el tope del lote. Se decide cuando una
   * emisión real muera y se vea qué manda el rastreo después.
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
