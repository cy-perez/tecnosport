package co.tecnosport.api.application.pago;

/**
 * Lo que trae la notificación de Sistecrédito, ya leído del cuerpo. Son los tres campos que la guía
 * {@code G-ALI-08} manda contrastar contra la consulta: {@code _id}, {@code invoice} y {@code
 * transactionStatus}.
 */
public record ProcesarNotificacionSistecreditoComando(
    String idTransaccion, String referencia, String estado) {}
