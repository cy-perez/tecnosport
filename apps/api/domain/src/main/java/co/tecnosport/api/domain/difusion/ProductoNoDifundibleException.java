package co.tecnosport.api.domain.difusion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * El producto no está en condiciones de publicarse en una red.
 *
 * <p><b>Una sola excepción para todos los motivos</b> —sin imagen, con una imagen que la red no
 * sabe leer, sin precio publicable, todavía en borrador— y no una clase por motivo. El {@code
 * codigo} del ProblemDetail sale del nombre de la clase ({@code apps/api/CLAUDE.md}), así que
 * varias clases serían varios códigos que el panel tendría que cablear y {@code CodigosDeCableTest}
 * fijar, para acabar enseñando el mismo cartel: «este producto todavía no se puede difundir, y esto
 * es lo que le falta». El detalle va en el mensaje, que es lo que el panel pinta.
 */
public class ProductoNoDifundibleException extends ExcepcionDeDominio {

  public ProductoNoDifundibleException(String mensaje) {
    super(mensaje);
  }

  public static ProductoNoDifundibleException sinImagen(UUID productoId) {
    return new ProductoNoDifundibleException(
        "El producto "
            + productoId
            + " no tiene imagen principal, y una publicación en Facebook o Instagram es una"
            + " imagen: sin ella no hay nada que publicar.");
  }

  public static ProductoNoDifundibleException sinVistaPrevia(UUID productoId) {
    return new ProductoNoDifundibleException(
        "Ninguna imagen del producto "
            + productoId
            + " está en un formato que Meta sepa descargar. El sitio sirve AVIF, que Meta no"
            + " entiende, y ninguna de estas tiene la vista previa en JPEG que se genera al"
            + " subirla: hay que volver a subirlas.");
  }

  /**
   * La red tiene sus propias reglas sobre la imagen —Instagram rechaza las muy altas o muy anchas—
   * y ninguna de las del producto las cumple.
   */
  public static ProductoNoDifundibleException sinImagenQueLaRedAdmita(
      UUID productoId, RedSocial red) {
    return new ProductoNoDifundibleException(
        "Ninguna foto del producto "
            + productoId
            + " cumple lo que "
            + red
            + " exige de una imagen, así que no queda ninguna que publicar allí. En Instagram eso"
            + " suele ser la proporción: no admite fotos más altas que 4:5 ni más anchas que"
            + " 1.91:1.");
  }

  public static ProductoNoDifundibleException sinPrecio(UUID productoId) {
    return new ProductoNoDifundibleException(
        "El producto "
            + productoId
            + " no tiene ninguna variante activa, así que no hay un precio que anunciar.");
  }

  public static ProductoNoDifundibleException noPublicado(UUID productoId) {
    return new ProductoNoDifundibleException(
        "El producto "
            + productoId
            + " todavía no está publicado en la tienda. Anunciarlo llevaría a la gente a una"
            + " página que devuelve 404.");
  }
}
