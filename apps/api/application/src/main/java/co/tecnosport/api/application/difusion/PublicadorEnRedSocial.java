package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.difusion.RedSocial;

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
   * Publica y devuelve lo que la red conteste. <b>No lanza cuando la red rechaza</b>: un rechazo es
   * una respuesta que el caso de uso tiene que guardar —el motivo acaba en la ficha del panel para
   * que alguien decida si reintenta—, no un fallo que deba romper la transacción. Las excepciones
   * quedan para lo que de verdad es excepcional: que el adaptador no pueda ni hablar con la red.
   */
  ResultadoPublicacion publicar(RedSocial red, String urlImagen, String pieDeFoto);
}
