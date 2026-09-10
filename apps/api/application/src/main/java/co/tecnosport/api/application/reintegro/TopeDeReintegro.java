package co.tecnosport.api.application.reintegro;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Ningún pedido devuelve más de lo que entró por él, contando lo ya devuelto <b>que este sistema
 * registró</b> y no solo la operación en curso.
 *
 * <p>Ese matiz —"que este sistema registró"— es un hueco conocido y conviene leerlo antes de
 * confiar en el tope: una reversión resuelta como {@code REVERTIDO_POR_EL_EMISOR} devuelve el
 * dinero por la red de pagos y <b>no deja {@code Reintegro}</b>, a propósito, porque registrar un
 * pago que no hicimos descuadraría la única pregunta que la constancia responde. Como no lo deja,
 * tampoco consume este tope: un contracargo seguido de un retracto sobre el mismo pedido devuelve
 * el total dos veces y el tope lo deja pasar. Cerrarlo exige decidir dos cosas que no son de
 * programación —si un contracargo se cuenta como devolución total, y qué hacer cuando el emisor
 * revirtió solo una parte, que {@code SolicitudReversion} hoy no guarda—.
 *
 * <p>Existe porque los cuatro caminos que devuelven dinero —retracto, garantía, reversión y
 * cancelación— llevaban cada uno su propia copia de la comparación, y las cuatro miraban lo mismo:
 * que el monto de <i>esa</i> operación no pasara del total del pedido. Ninguna miraba lo que ya se
 * había devuelto antes, así que un pedido de 500.000 admitía un reintegro de 500.000 por retracto y
 * otro de 500.000 por garantía, cada uno válido por separado. La consulta que lo destapa —{@link
 * RepositorioReintegros#buscarPorPedido}— existía desde que se creó el puerto y no la llamaba
 * nadie: servía para pintar pantallas.
 *
 * <p>Vive en aplicación y no en {@code Reintegro} por el mismo motivo que ya tenía escrito {@link
 * MontoDeReintegroInvalidoException}: la regla necesita el total del pedido, que es otro agregado,
 * y ahora también la suma de las constancias anteriores, que son varias filas. Un agregado no puede
 * comprobar una invariante que no cabe dentro de él.
 *
 * <p>Colaborador compartido y no un caso de uso, por el mismo motivo que {@code
 * AplicadorDeResultadoDePago} existe: la regla es una, y la tiene que aplicar igual quien cierre un
 * retracto y quien resuelva una garantía. La <b>forma</b> sí es distinta y conviene no confundirlas
 * al buscarla — aquél es una utilidad estática sin estado, éste es un {@code bean} con el
 * repositorio dentro, inyectado por constructor y declarado en {@code ConfiguracionReintegro}.
 * Corre dentro de la transacción de quien lo llama, así que la suma que lee incluye lo que esa
 * misma transacción haya escrito antes.
 *
 * <p>Bloquea en vez de advertir, a diferencia del medio preferido o de la vigencia de la garantía.
 * No es una decisión que le toque a una persona con el dato delante: si 500.001 de golpe se rechaza
 * desde la Fase 3, 500.000 más 500.000 en dos pasos no puede pasar.
 */
public final class TopeDeReintegro {

  private final RepositorioReintegros repositorio;

  public TopeDeReintegro(RepositorioReintegros repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  /**
   * Deja pasar el reintegro si cabe en lo que queda por devolver del pedido, y lo rechaza si no.
   *
   * <p>El caso de "cabe exacto" pasa a propósito: devolver el total completo es lo normal en un
   * retracto, y es lo que precarga el panel.
   *
   * <p><b>Lee y después escribe, sin bloqueo</b>, así que dos transacciones concurrentes con
   * orígenes distintos —un retracto y una garantía del mismo pedido— leen las dos lo mismo y las
   * dos pasan. {@code ux_reintegro_origen} no las separa, porque el origen es distinto. Con un solo
   * operador hacen falta dos pestañas para provocarlo; queda dicho aquí porque {@code ADR-0027}
   * apoya la decisión de no versionar los agregados en que el dinero está protegido, y esta parte
   * no lo está.
   */
  public void exigirQueQuepa(UUID pedidoId, Dinero total, Dinero nuevo) {
    Objects.requireNonNull(pedidoId, "El pedido del reintegro no puede ser nulo.");
    Objects.requireNonNull(total, "El total del pedido no puede ser nulo.");
    Objects.requireNonNull(nuevo, "El monto del reintegro no puede ser nulo.");
    Dinero yaDevuelto = yaDevuelto(pedidoId);
    if (yaDevuelto.valor().add(nuevo.valor()).compareTo(total.valor()) > 0) {
      throw yaDevuelto.valor().signum() == 0
          ? new MontoDeReintegroInvalidoException(nuevo, total)
          : new MontoDeReintegroInvalidoException(nuevo, yaDevuelto, total);
    }
  }

  /**
   * Lo devuelto hasta ahora por ese pedido, sumando los cinco motivos: la plata no sabe de cuál.
   */
  public Dinero yaDevuelto(UUID pedidoId) {
    Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    return Dinero.deCop(
        repositorio.buscarPorPedido(pedidoId).stream()
            .map(reintegro -> reintegro.monto().valor())
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }
}
