package co.tecnosport.api.application.pago;

/**
 * Nunca se traduce a un error HTTP hacia Wompi (docs/11-pagos-y-envios.md: "se descarta y se
 * registra") — el webhook siempre responde 200, para no entrar en el ciclo de reintentos de Wompi
 * por algo que un reintento no puede arreglar. Quien llame decide qué registrar con cada valor.
 */
public enum ResultadoEventoDePago {
  APLICADO,
  YA_PROCESADO,
  FIRMA_INVALIDA,
  PAGO_NO_ENCONTRADO,
  ESTADO_NO_SOPORTADO
}
