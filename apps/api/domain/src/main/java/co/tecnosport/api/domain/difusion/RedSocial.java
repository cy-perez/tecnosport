package co.tecnosport.api.domain.difusion;

/**
 * Dónde se difunde un producto.
 *
 * <p><b>Dos y no cuatro.</b> Las historias de Facebook y de Instagram también se pueden publicar
 * por la API, pero quedaron fuera del alcance a propósito y conviene que el porqué viva aquí y no
 * solo en un documento: una historia publicada por API <b>no admite stickers</b> —ni el de enlace,
 * ni encuesta, ni ubicación— y el etiquetado de producto tampoco funciona en ellas. Sería una
 * imagen muda: nadie puede tocar nada para llegar al producto. El día que Meta abra el sticker de
 * enlace, entran aquí dos valores más y el resto del agregado no cambia.
 *
 * <p>WhatsApp no está y no va a estar por esta puerta: los estados y los canales no tienen API
 * oficial. Lo que sí llega allá es el catálogo, que es otro camino y otro caso de uso.
 */
public enum RedSocial {
  /** La página de Facebook del negocio. Es la única red donde el enlace del pie es clicable. */
  FACEBOOK,

  /**
   * La cuenta profesional de Instagram. El pie no admite enlaces: lo que se escriba ahí es texto
   * muerto, y por eso su plantilla remite a la biografía en vez de pintar una URL.
   */
  INSTAGRAM
}
