package co.tecnosport.api.presentation.sugerencia.dto;

/**
 * Lo que manda el buzón del sitio.
 *
 * <p>{@code correo} vacío o nulo es «prefiero no decirlo», no un dato faltante: ver {@code
 * Sugerencia}. {@code autorizaDatos} solo se mira cuando hay correo, porque sin correo no hay dato
 * personal que autorizar.
 *
 * <p>Ni la IP ni la versión de la política viajan en el cuerpo, y es a propósito (regla dura #7):
 * la IP la resuelve {@code IpDelCliente} y la versión la fija el servidor. Si el navegador mandara
 * la versión que dice haber leído, bastaría con cambiarla para dejar constancia de una aceptación
 * que nunca ocurrió.
 */
public record EnviarSugerenciaRequest(String mensaje, String correo, boolean autorizaDatos) {}
