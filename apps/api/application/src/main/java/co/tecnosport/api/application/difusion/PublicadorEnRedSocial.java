package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.difusion.RedSocial;
import java.util.List;

/**
 * Publicar una imagen con su pie en una red social.
 *
 * <p><b>Un solo puerto para las dos redes</b>, al revés de lo que se hizo con las pasarelas de pago
 * —{@code PasarelaDePagos} y {@code PasarelaSistecredito} están separadas porque no comparten ni
 * una operación ({@code ADR-0048})—. Aquí sí la comparten: la operación es la misma, «publica esta
 * imagen con este texto», y lo que cambia son los viajes que hacen falta por debajo. Facebook es
 * uno y Instagram son dos, pero eso es asunto del adaptador y no de quien pide la publicación.
 *
 * <p>Si algún día entra una red cuya publicación necesite otra cosa —un video obligatorio, un
 * segundo texto— será el momento de partirlo, y no antes.
 */
public interface PublicadorEnRedSocial {

  /**
   * De las fotos que se le ofrecen, las que esta red admite, en el mismo orden y sin añadir
   * ninguna. Lista vacía si no admite ninguna.
   *
   * <p><b>Se pregunta antes de publicar, y no se deja que el adaptador descarte por su cuenta</b>,
   * porque lo que salga de aquí es lo que el caso de uso guarda en la constancia. Si el adaptador
   * dejara fuera una foto en silencio, la fila diría que se publicó algo que nunca salió — y esa
   * fila es justo lo que alguien va a mirar para saber qué vio la gente.
   *
   * <p>Qué admite cada red es cosa del adaptador: Instagram mira la proporción de cada imagen y
   * tiene un tope de cuántas caben en un carrusel; Facebook no pone ninguna de las dos.
   */
  List<ImagenAPublicar> admitidasPor(RedSocial red, List<ImagenAPublicar> imagenes);

  /**
   * Publica y devuelve lo que la red conteste. <b>No lanza cuando la red rechaza</b>: un rechazo es
   * una respuesta que el caso de uso tiene que guardar —el motivo acaba en la ficha del panel para
   * que alguien decida si reintenta—, no un fallo que deba romper la transacción. Las excepciones
   * quedan para lo que de verdad es excepcional: que el adaptador no pueda ni hablar con la red.
   *
   * <p>Con más de una imagen el post es un carrusel; con una, una foto suelta. Las que llegan aquí
   * son las que {@link #admitidasPor} dejó pasar.
   */
  ResultadoPublicacion publicar(RedSocial red, List<ImagenAPublicar> imagenes, String pieDeFoto);
}
