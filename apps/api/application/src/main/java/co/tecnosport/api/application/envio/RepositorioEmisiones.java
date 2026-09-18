package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEmisiones {

  /**
   * Guarda la emisión entera —su estado y sus identificadores de plataforma— como una sola unidad.
   *
   * <p>Lanza {@link EmisionYaEnCursoException} si el pedido ya tiene una emisión abierta. Eso lo
   * decide la base, no una lectura previa: entre leer y escribir cabe un segundo clic, y ese
   * segundo clic sería otro cobro. Que la excepción salga tipada de aquí es lo que impide que una
   * violación de unicidad de JPA se escape de {@code infrastructure} y termine en un 500 genérico.
   */
  void guardar(EmisionDeGuia emision);

  /**
   * La emisión abierta de un pedido, si la hay: solicitada, en curso o indeterminada. Hay como
   * mucho una, y quien pregunta es la puerta que evita pedir dos veces las guías del mismo pedido.
   */
  Optional<EmisionDeGuia> buscarAbiertaDePedido(UUID pedidoId);

  /**
   * Todas las emisiones de un pedido, de la más vieja a la más nueva.
   *
   * <p>La usa el reintento para dos cosas, y las dos cuestan plata si faltan: <strong>no volver a
   * elegir la transportadora que acaba de fallar</strong> —Coordinadora falla de forma determinista
   * y es la más barata, o sea la que el selector elige sola— y <strong>reconocer un envío que la
   * plataforma ya nos había dado</strong>, porque su caché de idempotencia devuelve los mismos
   * identificadores para la misma tarifa durante 96 horas.
   */
  List<EmisionDeGuia> buscarDePedido(UUID pedidoId);

  /**
   * Las que esperan respuesta de la plataforma y se pueden releer, de la más vieja a la más nueva:
   * si hay más de las que caben en un lote, la que lleva más rato esperando es la que más urge.
   */
  List<EmisionDeGuia> buscarEnCurso(int maximo);

  /**
   * Las que se pidieron y nunca llegaron a aceptarse, más viejas que el corte.
   *
   * <p>Una emisión se queda así cuando el proceso muere entre que se escribe la fila y que la
   * plataforma responde. Sin esta consulta se quedarían abiertas para siempre, bloqueando toda
   * emisión nueva de ese pedido y sin que nadie se entere: la fila existe justamente para que
   * alguien pueda ir a mirar si hubo cobro.
   */
  List<EmisionDeGuia> buscarSolicitadasAntesDe(Instant corte, int maximo);

  /**
   * Las que llevan en curso desde antes del corte: la plataforma nunca les dio desenlace.
   *
   * <p>Hermana de {@link #buscarSolicitadasAntesDe} y por el mismo motivo — un estado abierto sin
   * salida por tiempo se queda abierto para siempre, bloqueando su pedido en silencio.
   */
  List<EmisionDeGuia> buscarEnCursoAntesDe(Instant corte, int maximo);

  /** Una emisión concreta, para quien la señala desde la bandeja. */
  Optional<EmisionDeGuia> buscarPorId(UUID id);

  /**
   * La emisión que creó un envío concreto en la plataforma, si la conocemos.
   *
   * <p>Es {@link #buscarDePedido(UUID)} al revés, y existe por cómo habla la plataforma de dinero:
   * los cobros extra se identifican por <strong>envío</strong> y nunca nombran el pedido. Sin esta
   * búsqueda, el aviso de un sobrecosto dice "el envío 8c2eb7a4… costó 4.300 de más" y quien lo lee
   * tiene que ir al panel a averiguar de qué compra hablaba.
   *
   * <p>Vacío es un caso normal y no un fallo: el cobro puede venir de una guía que alguien emitió
   * por fuera y tecleó en el panel, que no tiene emisión nuestra. Quien pregunte tiene que poder
   * seguir sin la respuesta — el dinero ya salió del crédito igual.
   */
  Optional<EmisionDeGuia> buscarPorEnvioEnPlataforma(String envioEnPlataforma);

  /**
   * Las que tienen plata comprometida y nadie ha desenredado —{@code INDETERMINADA} y {@code
   * PARCIAL}—, de la más vieja a la más nueva.
   *
   * <p>Ninguna de las dos se resuelve sola: la plataforma no va a decir nada nuevo de ellas. Si
   * nadie las mira se quedan así para siempre, con guías pagadas sin usar o con un cobro del que no
   * sabemos si ocurrió, y por eso son la mitad de la bandeja de revisión.
   */
  List<EmisionDeGuia> buscarQueExigenOjoHumano(int maximo);
}
