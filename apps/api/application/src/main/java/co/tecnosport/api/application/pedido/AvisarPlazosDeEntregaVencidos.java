package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.PlazoDeEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Le avisa al comprador cuando vencieron los treinta días calendario para entregarle, y no hace
 * nada más.
 *
 * <p>Los términos publicados prometen dos cosas en "Envío y entrega": el plazo legal del artículo
 * 18 de la Ley 1480 de 2011, y que si no se cumple el comprador "puede terminar el contrato y
 * recuperar su dinero". Lo segundo ya tenía código detrás —{@code CancelarPedido} con {@code
 * PLAZO_INCUMPLIDO}— pero nadie miraba lo primero: las dos únicas tareas programadas eran la purga
 * de carritos y la conciliación de Wompi, así que un pedido pagado y sin despachar incumplía en
 * silencio y el comprador se enteraba solo si preguntaba.
 *
 * <p><b>No cancela ni reintegra nada</b>, y esa es la decisión de {@code ADR-0028}: el artículo 18
 * le da al comprador la opción de terminar el contrato, no obliga al negocio a deshacerlo por su
 * cuenta. Puede preferir esperar, y cancelarle el pedido sin preguntarle sería decidir por él.
 * Quien decide es una persona —el comprador escribiendo, o el panel— y entonces sí corre {@code
 * CancelarPedido}.
 *
 * <p><b>Tampoco radica una solicitud de atención</b>, por el mismo motivo que {@code
 * CancelarPedido} no la radica: la bandeja de PQR es de peticiones del comprador con su plazo de
 * respuesta corriendo, y esto es el negocio avisando de algo suyo.
 *
 * <p>Cubre también los <b>despachados sin entregar</b>, y conviene saber por qué: el plazo legal
 * corre hasta la entrega, no hasta el despacho, así que dejarlos fuera abriría un hueco real
 * —treinta días con la mercancía en tránsito siguen siendo un incumplimiento—. El riesgo es el
 * contrario: como la entrega la marca hoy una persona en el panel, un despachado vencido suele ser
 * un descuido de operación, y el correo le llegaría a alguien que ya tiene el producto. Por eso el
 * texto de ese caso no acusa a nadie y pide que lo corrijan si ya llegó.
 */
public final class AvisarPlazosDeEntregaVencidos {

  /**
   * Los estados en los que un pedido tiene un plazo de entrega corriendo y todavía no se cumplió.
   *
   * <p>Vive aquí y no en la consulta porque es una regla, no un detalle de SQL. Quedan fuera {@code
   * PAGO_PENDIENTE} y {@code PAGO_FALLIDO} —el plazo ni siquiera arrancó, no hay contrato que
   * incumplir—, {@code CANCELADO}, y todos los posteriores a la entrega: ahí el plazo se cumplió o
   * el camino ya tiene su propio desenlace.
   */
  private static final Set<EstadoPedido> CON_ENTREGA_PENDIENTE =
      EnumSet.of(
          EstadoPedido.CONFIRMADO_CONTRAENTREGA,
          EstadoPedido.PAGADO,
          EstadoPedido.EN_PREPARACION,
          EstadoPedido.DESPACHADO);

  private final RepositorioPedidos repositorioPedidos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;

  public AvisarPlazosDeEntregaVencidos(
      RepositorioPedidos repositorioPedidos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoVigilanciaPlazos ejecutar() {
    Instant ahora = reloj.ahora();
    List<Pedido> candidatos =
        repositorioPedidos.buscarSinAvisoDePlazo(
            CON_ENTREGA_PENDIENTE, ahora.minus(PlazoDeEntrega.DIAS_CALENDARIO, ChronoUnit.DAYS));

    int avisados = 0;
    for (Pedido pedido : candidatos) {
      if (vencio(pedido, ahora)) {
        pedido.marcarAvisoDePlazoEnviado(ahora);
        repositorioPedidos.guardar(pedido);
        avisar(pedido);
        avisados++;
      }
    }
    return new ResultadoVigilanciaPlazos(candidatos.size(), avisados);
  }

  /**
   * El filtro de la consulta es grueso —acota por {@code creadoEn}, que es anterior al inicio del
   * plazo— así que la última palabra la tiene el dominio, sobre la fecha real del historial. Un
   * pedido sin fecha de inicio no puede haber vencido: el plazo no arrancó.
   */
  private static boolean vencio(Pedido pedido, Instant ahora) {
    return pedido
        .fechaDeInicioDelPlazoDeEntrega()
        .map(inicio -> PlazoDeEntrega.verdicto(inicio, ahora) == VerdictoPlazo.VENCIDO)
        .orElse(false);
  }

  /**
   * Se marca y se guarda <b>antes</b> de escribir, no después, y no es un descuido del orden: el
   * envío ocurre dentro de esta misma transacción y el adaptador de producción se traga los fallos
   * ({@link EnviadorDeCorreo}). Con el orden contrario, un correo que revienta de otra forma
   * dejaría el pedido sin marcar y el vigilante volvería a escribirle en la siguiente vuelta, cada
   * doce horas, hasta que alguien lo despachara. Prefiero un aviso perdido que uno repetido.
   */
  private void avisar(Pedido pedido) {
    boolean yaDespachado = pedido.estado() == EstadoPedido.DESPACHADO;
    boolean huboCobro = pedido.dineroRecibido().valor().compareTo(BigDecimal.ZERO) > 0;

    String cuerpo = textos.texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CUERPO);
    if (yaDespachado) {
      cuerpo += textos.texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_EN_CAMINO);
    }
    cuerpo +=
        textos.texto(
            huboCobro
                ? TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CON_DINERO
                : TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_SIN_COBRO);
    cuerpo += textos.texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CIERRE);

    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_ASUNTO, pedido.numeroPedido().valor()),
        cuerpo);
  }
}
