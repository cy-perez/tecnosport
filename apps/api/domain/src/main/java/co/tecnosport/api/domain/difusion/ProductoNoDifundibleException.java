package co.tecnosport.api.domain.difusion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * El producto no está en condiciones de publicarse en una red.
 *
 * <p><b>Una sola excepción para los tres motivos</b> —sin imagen, sin precio publicable, todavía en
 * borrador— y no tres clases. El {@code codigo} del ProblemDetail sale del nombre de la clase
 * ({@code apps/api/CLAUDE.md}), así que tres clases serían tres códigos que el panel tendría que
 * cablear y {@code CodigosDeCableTest} fijar, para acabar enseñando el mismo cartel: «este producto
 * todavía no se puede difundir, y esto es lo que le falta». El detalle va en el mensaje, que es lo
 * que el panel pinta.
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
        "La imagen principal del producto "
            + productoId
            + " no tiene vista previa en JPEG. Meta descarga la imagen por URL y no entiende AVIF:"
            + " hay que volver a subir la imagen para que se genere.");
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
