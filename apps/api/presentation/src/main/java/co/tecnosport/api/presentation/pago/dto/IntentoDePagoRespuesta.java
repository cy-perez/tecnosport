package co.tecnosport.api.presentation.pago.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;

/**
 * Lo que el frontend necesita para abrir el Web Checkout hospedado de Wompi
 * (docs/11-pagos-y-envios.md): construye la URL o el widget con estos datos, firmados por el
 * servidor, y Wompi resuelve el resto — captura de tarjeta, PSE, Nequi, Bancolombia, Addi.
 */
public record IntentoDePagoRespuesta(
    String referencia,
    DineroRespuesta monto,
    String firmaIntegridad,
    String llavePublica,
    String ambiente) {}
