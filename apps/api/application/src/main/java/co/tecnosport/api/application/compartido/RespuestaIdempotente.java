package co.tecnosport.api.application.compartido;

/**
 * La respuesta HTTP completa que se guardó para una llave de idempotencia, lista para repetirla.
 */
public record RespuestaIdempotente(int estadoHttp, String tipoContenido, String cuerpo) {}
