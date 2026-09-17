package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEnvios {

  void guardar(Envio envio);

  Optional<Envio> buscarPorPedidoId(UUID pedidoId);

  /**
   * Por número de guía, que es con lo que llegan los eventos de la transportadora.
   *
   * <p>Sin la transportadora: dos guías iguales de empresas distintas son posibles en teoría y
   * nadie las ha visto en la práctica. El día que aparezcan, el síntoma será un evento aplicado al
   * envío equivocado, y la corrección es pedir también la transportadora aquí.
   */
  Optional<Envio> buscarPorGuia(String guia);

  /**
   * Los envíos que llevan callados desde antes del corte: despachados hace rato y sin ningún evento
   * recibido después. Son los que la conciliación va a preguntarle a la transportadora (adr/0022).
   *
   * <p>Los que ya terminaron quedan fuera: un envío entregado, devuelto, cancelado o destruido no
   * tiene más historia que contar, y seguir preguntando por él gastaría cuota de un proveedor
   * limitado a dos peticiones por segundo.
   */
  List<Envio> buscarSinEventosDesde(Instant corte, int maximo);

  /**
   * Los envíos que tienen alguna guía cuyo último movimiento la dejó quieta: excepción, retención,
   * cancelación, destrucción o fallo. Son los candidatos de la bandeja de revisión.
   *
   * <p><strong>El filtro es grueso a propósito y no decide nada.</strong> Puede traer algún envío
   * de más —dos eventos de la misma guía con el mismo instante hacen que "el último" no sea uno
   * solo desde el punto de vista de una consulta— y quién pide de verdad ojo humano lo dice {@code
   * GuiaEnvio.ultimoEstado()} al leer el agregado. Escribir aquí la regla entera daría dos
   * definiciones de "último estado" capaces de divergir, y de esas dos solo una tiene pruebas.
   *
   * <p>{@code maximo} acota el lote por lo mismo que en {@link #buscarSinEventosDesde}: una caída
   * larga del webhook puede dejar muchos paquetes quietos a la vez, y una pantalla no puede traerse
   * la tabla entera.
   */
  List<Envio> buscarConGuiasEnRevision(int maximo);
}
