package co.tecnosport.api.application.compartido;

/**
 * La respuesta HTTP completa que se guardó para una llave de idempotencia, lista para repetirla, y
 * el método y la ruta de la petición que la produjo.
 *
 * <p>{@code metodo} y {@code ruta} son lo que impide repetirle a una petición la respuesta de otro
 * recurso: la llave la elige el cliente, y una misma llave mandada a {@code /pagos/intentos} y
 * después a {@code /pedidos} devolvía el intento de pago como si fuera el pedido creado. Al
 * completar todavía no se conocen —el repositorio los tiene desde {@code reclamar}— y van nulos.
 */
public record RespuestaIdempotente(
    int estadoHttp, String tipoContenido, String cuerpo, String metodo, String ruta) {

  public RespuestaIdempotente(int estadoHttp, String tipoContenido, String cuerpo) {
    this(estadoHttp, tipoContenido, cuerpo, null, null);
  }

  /** Si la respuesta se guardó para esta misma operación. */
  public boolean esDe(String metodoPedido, String rutaPedida) {
    return metodoPedido.equals(metodo) && rutaPedida.equals(ruta);
  }
}
