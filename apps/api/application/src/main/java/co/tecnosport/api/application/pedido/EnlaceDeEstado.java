package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * El enlace que abre la pantalla de estado de un pedido sin sesión: el id, que es un UUID v7 y no
 * se adivina, y el correo que lo autoriza.
 *
 * <p>Estaba copiado en {@code EnviarComprobantesDeCompra} y en {@code DespacharPedido}, y con
 * {@code SolicitarEnlaceDeSeguimiento} iban a ser tres (ADR-0088). Es el único enlace que la
 * pantalla sabe leer: una copia que divergiera mandaría a una pantalla que no encuentra el pedido.
 */
final class EnlaceDeEstado {

  private EnlaceDeEstado() {}

  /**
   * Los dos van codificados — el correo lleva una arroba y puede llevar un {@code +}, que en una
   * cadena de consulta significa un espacio.
   */
  static String de(String urlBaseEstado, Pedido pedido) {
    return urlBaseEstado
        + "?pedidoId="
        + URLEncoder.encode(pedido.id().toString(), StandardCharsets.UTF_8)
        // En el fragmento y no en la consulta: el navegador nunca manda lo que va después del #,
        // así que el correo no queda en los registros del servidor ni en el Referer. La pantalla de
        // estado lo lee de ahí.
        + "#correo="
        + URLEncoder.encode(pedido.correo().valor(), StandardCharsets.UTF_8);
  }
}
