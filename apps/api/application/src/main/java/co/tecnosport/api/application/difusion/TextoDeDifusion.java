package co.tecnosport.api.application.difusion;

/**
 * Cada pieza de texto con la que se arma el pie de una publicación en redes, por su llave.
 *
 * <p>Es lo mismo que {@code TextoDeCorreo} y por lo mismo: el armador decide el orden y los datos,
 * y lo que se lee vive en {@code difusion_es.properties} y {@code difusion_en.properties}, fuera
 * del código (regla dura #4, {@code ADR-0102}). Hasta el 30 de octubre de 2026 «desde» y «Enlace en
 * la bio» estaban escritos dentro del armador, en un solo idioma, con un formato que ningún
 * guardián miraba.
 */
public enum TextoDeDifusion {
  /** La primera línea: nombre y precio. Es lo que se ve antes del «ver más». */
  ENTRADA("entrada"),
  PRECIO_UNICO("precio.unico"),
  PRECIO_DESDE("precio.desde"),
  VINETA("vineta"),
  ATRIBUTO_COLOR("atributo.color"),
  ATRIBUTO("atributo.otro"),
  CONFIANZA_ENVIO("confianza.envio"),

  /**
   * Con contraentrega, y solo cuando el producto puede pagarse así: la línea de catálogo no está
   * excluida y su precio no pasa del tope. La cobertura de la ciudad no se puede saber al publicar,
   * y por eso el texto la nombra.
   */
  CONFIANZA_PAGO_CON_CONTRAENTREGA("confianza.pago_con_contraentrega"),
  CONFIANZA_PAGO_EN_LINEA("confianza.pago_en_linea"),
  CONFIANZA_WHATSAPP("confianza.whatsapp"),
  LLAMADO_FACEBOOK("llamado.facebook"),
  LLAMADO_INSTAGRAM("llamado.instagram");

  private final String clave;

  TextoDeDifusion(String clave) {
    this.clave = clave;
  }

  public String clave() {
    return clave;
  }
}
