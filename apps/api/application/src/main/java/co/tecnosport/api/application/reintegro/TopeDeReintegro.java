package co.tecnosport.api.application.reintegro;

import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Ningún pedido devuelve más de lo que entró por él, contando <b>todo</b> lo que volvió al
 * comprador y no solo la operación en curso.
 *
 * <p>"Todo" son dos fuentes, y hasta hace poco era una. Las constancias de {@code Reintegro} son el
 * dinero que salió de nuestra caja. La otra es la reversión que hizo <b>el emisor</b>: ese
 * desenlace no deja {@code Reintegro} a propósito —el dinero volvió por la red de pagos, y
 * registrar un pago que no hicimos descuadraría la única pregunta que la constancia responde—, así
 * que durante un tiempo tampoco consumía este tope, y un contracargo seguido de un retracto
 * devolvía el total dos veces. Lo levantó una revisión adversarial de los caminos del dinero. La
 * salida no fue inventar la constancia sino anotar el hecho: {@code
 * SolicitudReversion.montoRevertidoPorElEmisor}, que quien resuelve sabe porque se lo dijo el
 * emisor, y que además da respuesta a la reversión parcial que contemplan el artículo 51 y el
 * Decreto 587 de 2016.
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
  private final RepositorioSolicitudesReversion reversiones;

  public TopeDeReintegro(
      RepositorioReintegros repositorio, RepositorioSolicitudesReversion reversiones) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reversiones = Objects.requireNonNull(reversiones);
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
   * Lo que ya volvió al comprador por ese pedido: las constancias de los cinco motivos más lo que
   * revirtió el emisor. La plata no sabe por qué camino salió ni quién la movió.
   */
  public Dinero yaDevuelto(UUID pedidoId) {
    Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    BigDecimal deNuestraCaja =
        repositorio.buscarPorPedido(pedidoId).stream()
            .map(reintegro -> reintegro.monto().valor())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal delEmisor =
        reversiones.buscarPorPedidoId(pedidoId).stream()
            .map(reversion -> reversion.montoRevertidoPorElEmisor().orElse(null))
            .filter(Objects::nonNull)
            .map(Dinero::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Dinero.deCop(deNuestraCaja.add(delEmisor));
  }
}
