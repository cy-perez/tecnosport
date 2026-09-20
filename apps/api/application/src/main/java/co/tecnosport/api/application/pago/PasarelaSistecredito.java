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
   * Pide la creación de la transacción y devuelve el resultado <b>ya resuelto</b>: con la URL a la
   * que mandar al comprador, o con el estado terminal que explica por qué no la hay.
   *
   * <p>La pasarela no entrega la URL en la respuesta de creación —sigue hablando con el medio de
   * pago— y hay que consultar hasta que aparezca. Ese sondeo lo hace el adaptador y no quien llama:
   * es una particularidad del protocolo de este proveedor, y un caso de uso que tuviera que dormir
   * un hilo entre reintentos estaría haciendo de cliente HTTP.
   *
   * <p>Puede devolver una transacción sin URL y sin estado terminal: significa que la pasarela se
   * quedó pensando más de lo que el sondeo espera. La transacción existe y tiene id; hay que
   * guardarla para que la conciliación la resuelva, no descartarla.
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
