package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Le manda a quien compró el comprobante de su compra: qué llevó, cuánto pagó, cómo y a dónde se le
 * entrega, y quién le vendió.
 *
 * <p><b>No es una factura de venta y el texto lo dice.</b> El negocio es una persona natural no
 * responsable de IVA, y por eso está entre los <b>no obligados a expedir factura</b> —art.
 * 1.6.1.4.3 del Decreto 1625 de 2016, desarrollado por la Resolución DIAN 000165 de 2023—. La misma
 * resolución cierra la puerta de en medio en el parágrafo 1 de su art. 8: quien <i>opta</i> por
 * facturar "se considera para efectos tributarios obligado a facturar", con validación previa,
 * habilitación y numeración autorizada, y ya no puede dejar de hacerlo. Un documento que se llamara
 * factura sin serlo tendría lo peor de los dos lados. Ver {@code adr/0041} y {@code adr/0042}.
 *
 * <p><b>Por qué es una tarea y no una llamada dentro de cada camino.</b> Un pedido queda en firme
 * por cuatro caminos distintos —contraentrega verificada, pago en línea aprobado por webhook, el
 * mismo pago descubierto por la conciliación, y la transferencia manual conciliada a mano— y los
 * cuatro tendrían que acordarse de mandarlo. Aquí la regla es una sola y se enuncia sobre el
 * estado: <i>todo pedido en firme tiene su comprobante</i>. De paso, mandar correos no toca el
 * camino del dinero: un fallo de SMTP no tiene por dónde estropear un pago que se está aplicando.
 *
 * <p>El precio es que llega con el retraso de la tarea, no en el mismo segundo. Es un intercambio
 * aceptado: el comprobante es el soporte de la compra, no el acuse de que el pago entró — eso ya lo
 * ve en la pantalla de estado apenas vuelve del checkout.
 */
public final class EnviarComprobantesDeCompra {

  /**
   * Los estados en los que la compra está hecha y sigue en pie.
   *
   * <p>Quedan fuera los tres previos —{@code CREADO}, {@code PAGO_PENDIENTE}, {@code PAGO_FALLIDO}—
   * porque ahí todavía no hay compra que comprobar, y quedan fuera {@code CANCELADO}, {@code
   * DEVUELTO} y {@code RECHAZADO_EN_ENTREGA} porque ahí la compra se deshizo: "gracias por tu
   * compra" sería el mensaje equivocado, y esos tres caminos ya tienen su propio correo.
   *
   * <p>Eso deja un hueco teórico —un pedido que se confirme y se deshaga dentro del intervalo de la
   * tarea no recibiría comprobante— y se acepta a sabiendas: la tarea corre cada pocos minutos y
   * esos tres estados están a días de distancia. El día que el intervalo se alargue, esto hay que
   * volver a mirarlo.
   */
  private static final Set<EstadoPedido> EN_FIRME =
      EnumSet.of(
          EstadoPedido.CONFIRMADO_CONTRAENTREGA,
          EstadoPedido.PAGADO,
          EstadoPedido.EN_PREPARACION,
          EstadoPedido.DESPACHADO,
          EstadoPedido.ENTREGADO,
          EstadoPedido.RECAUDO_PENDIENTE,
          EstadoPedido.RECAUDO_CONCILIADO);

  private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final RepositorioPedidos repositorioPedidos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final String urlBaseEstado;

  public EnviarComprobantesDeCompra(
      RepositorioPedidos repositorioPedidos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      String urlBaseEstado) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.urlBaseEstado = Objects.requireNonNull(urlBaseEstado);
  }

  public ResultadoComprobantes ejecutar() {
    Instant ahora = reloj.ahora();
    List<Pedido> pendientes = repositorioPedidos.buscarSinComprobante(EN_FIRME);

    int enviados = 0;
    int fallidos = 0;
    for (Pedido pedido : pendientes) {
      // Reclamar antes de escribir, y solo escribir si se ganó el reclamo: el que pierde es otra
      // instancia que ya le mandó el comprobante a este mismo comprador.
      if (!repositorioPedidos.reclamarComprobante(pedido.id(), ahora)) {
        continue;
      }
      try {
        enviar(pedido);
        enviados++;
      } catch (RuntimeException fallo) {
        // Devolver el reclamo y seguir con los demás. Sin esto, un fallo en el pedido veinte dejaba
        // su marca puesta —o sea, ese comprador sin comprobante para siempre, porque la consulta ya
        // no lo ve— y además abortaba el lote entero, castigando a los que venían detrás por un
        // problema que no era suyo. Lo levantó una revisión adversarial.
        repositorioPedidos.liberarComprobante(pedido.id());
        fallidos++;
      }
    }
    return new ResultadoComprobantes(pendientes.size(), enviados, fallidos);
  }

  private void enviar(Pedido pedido) {
    StringBuilder cuerpo = new StringBuilder();
    cuerpo.append(
        textos.texto(
            TextoDeCorreo.PEDIDO_COMPROBANTE_CUERPO,
            pedido.numeroPedido().valor(),
            FECHA.format(pedido.creadoEn().atZone(ZONA_COLOMBIA))));
    for (LineaPedido linea : pedido.lineas()) {
      cuerpo.append(
          textos.texto(
              TextoDeCorreo.PEDIDO_COMPROBANTE_LINEA,
              linea.cantidad(),
              linea.nombre(),
              linea.sku().valor(),
              textos.dinero(linea.subtotal())));
    }
    cuerpo.append(
        textos.texto(
            TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES,
            textos.dinero(pedido.subtotal()),
            textos.dinero(pedido.costoEnvio()),
            textos.dinero(pedido.total())));
    cuerpo.append(textos.texto(entrega(pedido)));
    cuerpo.append(textos.texto(pago(pedido)));
    cuerpo.append(textos.texto(TextoDeCorreo.PEDIDO_COMPROBANTE_VENDEDOR));
    cuerpo.append(textos.texto(TextoDeCorreo.PEDIDO_COMPROBANTE_CIERRE, enlaceDeEstado(pedido)));

    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.PEDIDO_COMPROBANTE_ASUNTO, pedido.numeroPedido().valor()),
        cuerpo.toString());
  }

  private static TextoDeCorreo entrega(Pedido pedido) {
    return pedido.tipoEntrega() == TipoEntrega.RETIRO_EN_PUNTO
        ? TextoDeCorreo.PEDIDO_COMPROBANTE_ENTREGA_RETIRO
        : TextoDeCorreo.PEDIDO_COMPROBANTE_ENTREGA_DOMICILIO;
  }

  /**
   * El método que el comprador eligió, no el que la pasarela reportó. Son dos hechos distintos —el
   * Web Checkout hospedado permite que no coincidan, y {@code Pago} guarda el segundo— y el que
   * corresponde a un comprobante es el primero: es lo que esa persona aceptó pagar.
   */
  private static TextoDeCorreo pago(Pedido pedido) {
    // Sin `default`, por lo mismo que MetodoPago.seProcesaPorPasarela(): un método nuevo en ese
    // enum no compila hasta que alguien decida qué dice el comprobante de una compra pagada así.
    return switch (pedido.metodoPago()) {
      case CONTRAENTREGA -> TextoDeCorreo.PEDIDO_COMPROBANTE_PAGO_CONTRAENTREGA;
      case TRANSFERENCIA_MANUAL -> TextoDeCorreo.PEDIDO_COMPROBANTE_PAGO_TRANSFERENCIA;
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI, SISTECREDITO ->
          TextoDeCorreo.PEDIDO_COMPROBANTE_PAGO_EN_LINEA;
    };
  }

  private String enlaceDeEstado(Pedido pedido) {
    return urlBaseEstado
        + "?pedidoId="
        + URLEncoder.encode(pedido.id().toString(), StandardCharsets.UTF_8)
        + "&correo="
        + URLEncoder.encode(pedido.correo().valor(), StandardCharsets.UTF_8);
  }
}
