package co.tecnosport.api.presentation.pago.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;

/**
 * Lo que el frontend necesita para abrir el Web Checkout hospedado de Wompi
 * (docs/11-pagos-y-envios.md): construye la URL o el widget con estos datos, firmados por el
 * servidor, y Wompi resuelve el resto — captura de tarjeta, PSE y botón de Bancolombia. Cuál de los
 * tres lo elige el comprador en la pantalla de Wompi, no aquí: por eso el dominio los agrupa en un
 * solo {@code MetodoPago.WOMPI}. (Nombraba también a Addi, que salió del enum en la V61, y a Nequi,
 * que desde el 28 de septiembre de 2026 se recibe como transferencia manual.)
 */
public record IntentoDePagoRespuesta(
    String referencia,
    DineroRespuesta monto,
    String firmaIntegridad,
    String llavePublica,
    String ambiente) {}
