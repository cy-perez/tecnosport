package co.tecnosport.api.application.pago;

import java.util.Optional;

/**
 * Puerto hacia la pasarela de Sistecrédito ({@code adr/0048}, guías {@code G-ALI-08} y {@code
 * G-ALI-12}). Implementación de producción: cliente HTTP contra {@code api.credinet.co}, en
 * infrastructure. Implementación de prueba: una que responde sin red.
 *
 * <p><b>Es un puerto aparte de {@link PasarelaDePagos} y no una implementación suya</b> porque las
 * dos pasarelas no comparten una sola operación con la misma semántica: aquí no hay firma de
 * integridad que generar ni checksum de webhook que verificar, y la creación de la transacción —que
 * en Wompi ocurre en el navegador— aquí es una llamada del servidor.
 */
public interface PasarelaSistecredito {

  /**
   * Pide la creación de la transacción ({@code POST /pay/create}). La respuesta llega con estado
   * {@code PendingForPaymentMethod} y <b>casi nunca</b> con la URL de redirección: la pasarela
   * todavía está hablando con el medio de pago. Quien llame tiene que sondear con {@link
   * #consultar} hasta que aparezca.
   *
   * @throws SistecreditoNoRespondeException si la pasarela no responde o responde algo ilegible. Un
   *     rechazo con cuerpo entendible no es esto: viene dentro de la {@link
   *     TransaccionSistecredito} con su código.
   */
  TransaccionSistecredito crear(SolicitudTransaccionSistecredito solicitud);

  /**
   * Consulta una transacción por el {@code _id} que devolvió {@link #crear} ({@code GET
   * /pay/GetTransactionResponse}).
   *
   * <p>{@code Optional.empty()} cuando la consulta falla por red o el id no existe — quien sondea
   * reintenta, y quien concilia lo intenta en la próxima corrida. No es un error de negocio.
   */
  Optional<TransaccionSistecredito> consultar(String idTransaccion);
}
