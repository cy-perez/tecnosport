package co.tecnosport.api.application.difusion;

/**
 * Cada pieza de texto con la que se arma el pie de una publicación en redes, por su llave.
 *
 * <p>Es lo mismo que {@code TextoDeCorreo} y por lo mismo: el armador decide el orden y los datos,
 * y lo que se lee vive en {@code difusion_es.properties} y {@code difusion_en.properties}, fuera
 * del código (regla dura #4, {@code ADR-0102}). Hasta el 30 de octubre de 2026 «desde» y «Enlace en
 * la bio» estaban escritos dentro del armador, en un solo idioma, con un formato que ningún
 * guardián miraba.
 *
 * <p>Los enlaces de WhatsApp y de las redes también son llaves ({@code url.*}) y no propiedades de
 * la aplicación: son los mismos del pie del sitio y de los correos, y {@code npm run datos-negocio}
 * los compara contra esos.
 */
public enum TextoDeDifusion {
  /** La primera línea: emoji, nombre y precio. Es lo que se ve antes del «ver más». */
  ENTRADA("entrada"),
  PRECIO_UNICO("precio.unico"),
  PRECIO_DESDE("precio.desde"),
  EMOJI_POR_OMISION("emoji.por_omision"),
  VINETA("vineta"),
  ATRIBUTO_TALLA("atributo.talla"),
  ATRIBUTO_COLOR("atributo.color"),
  ATRIBUTO("atributo.otro"),

  PAGO_TITULO("pago.titulo"),
  PAGO_TARJETA("pago.tarjeta"),

  /**
   * Se anuncia en todos los productos, aunque el checkout no la ofrezca en las líneas excluidas ni
   * por encima del tope: lo decidió el negocio el 5 de noviembre de 2026, y lo que no pase por el
   * checkout se coordina por WhatsApp ({@code ADR-0107}).
   */
  PAGO_CONTRAENTREGA("pago.contraentrega"),
  PAGO_TRANSFERENCIA("pago.transferencia"),

  /** Los créditos, cada uno solo si está habilitado y el producto alcanza su mínimo. */
  PAGO_CREDITO_ADDI_Y_SISTECREDITO("pago.credito.addi_y_sistecredito"),
  PAGO_CREDITO_ADDI("pago.credito.addi"),
  PAGO_CREDITO_SISTECREDITO("pago.credito.sistecredito"),
  PAGO_RECOGIDA("pago.recogida"),
  ENVIO("envio"),

  LLAMADO_FACEBOOK("llamado.facebook"),
  LLAMADO_INSTAGRAM("llamado.instagram"),
  WHATSAPP_FACEBOOK("whatsapp.facebook"),
  WHATSAPP_INSTAGRAM("whatsapp.instagram"),

  /** «Síguenos en Facebook», que va en los posts de Instagram. */
  REDES_FACEBOOK("redes.facebook"),

  /** «Síguenos en Instagram», que va en los posts de Facebook. */
  REDES_INSTAGRAM("redes.instagram"),
  SITIO("sitio"),

  URL_WHATSAPP("url.whatsapp"),
  URL_FACEBOOK("url.facebook"),
  URL_INSTAGRAM("url.instagram");

  private final String clave;

  TextoDeDifusion(String clave) {
    this.clave = clave;
  }

  public String clave() {
    return clave;
  }
}
