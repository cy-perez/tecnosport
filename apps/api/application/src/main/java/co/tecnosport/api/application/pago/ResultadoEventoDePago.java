package co.tecnosport.api.application.pago;

/**
 * Nunca se traduce a un error HTTP hacia Wompi (docs/11-pagos-y-envios.md: "se descarta y se
 * registra") — el webhook siempre responde 200, para no entrar en el ciclo de reintentos de Wompi
 * por algo que un reintento no puede arreglar. Quien llame decide qué registrar con cada valor.
 */
public enum ResultadoEventoDePago {
  APLICADO,

  /**
   * El pago y el pedido sí se actualizaron, pero la reserva de inventario de alguna línea ya no era
   * válida para confirmar o liberar (venció por tiempo, o ya se había resuelto antes) — riesgo de
   * sobreventa, necesita revisión manual. No pasa por el flujo normal: es la excepción, no la
   * regla.
   */
  APLICADO_SIN_CONFIRMAR_INVENTARIO,

  /**
   * El pago se aprobó, pero su pedido ya no lo esperaba: otro intento ya lo había pagado, o estaba
   * cancelado o fallido. El dinero entró y no pertenece a ninguna venta; el pago queda marcado y
   * aparece en el panel hasta que se registre su reintegro.
   */
  APROBADO_SIN_PEDIDO_QUE_LO_ESPERE,

  YA_PROCESADO,
  FIRMA_INVALIDA,

  /**
   * La firma es válida pero no cubre el estado: lo que mueve el pedido no estaría autenticado. No
   * se aplica; la conciliación, que consulta a la pasarela, lo resuelve.
   */
  ESTADO_SIN_FIRMAR,

  /**
   * El evento es de esta referencia, pero el monto o la moneda no son los del pago. No se aplica:
   * despacharlo sería entregar la mercancía por otro importe. Es la contraprueba que la
   * conciliación ya hacía y el webhook no.
   */
  MONTO_NO_COINCIDE,
  PAGO_NO_ENCONTRADO,
  ESTADO_NO_SOPORTADO
}
