package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Objects;

/**
 * Primer paso de la subida de una imagen de galería, gemelo de {@link
 * SolicitarSubidaDeImagenPrincipal}: valida el producto y el tipo de contenido, y pide al almacén
 * una URL firmada. La confirmación es {@link AgregarImagenDeGaleria}.
 *
 * <p><b>Comprueba el tope antes de firmar nada.</b> El agregado lo comprueba igual al agregar, así
 * que esto no protege ninguna invariante — lo que evita es que alguien suba veinte megas a Cloud
 * Storage para que el segundo paso le diga que no caben. Un objeto subido que nunca se confirma no
 * lo borra nadie: la limpieza por prefijo de la principal no toca el de galería, a propósito,
 * porque ahí un prefijo compartido se llevaría las imágenes hermanas.
 *
 * <p>El prefijo es {@code productos/{id}/galeria-}, distinto del {@code principal-} que {@link
 * ConfirmarImagenPrincipal} limpia entero cada vez que se reemplaza la principal.
 */
public final class SolicitarSubidaDeImagenDeGaleria {

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public SolicitarSubidaDeImagenDeGaleria(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public SolicitudDeSubida ejecutar(SolicitarSubidaDeImagenDeGaleriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    producto.verificarQueCabeOtraImagenEnLaGaleria();

    String extension = TiposDeImagen.extensionDe(comando.contentType());

    String objectKey =
        "productos/"
            + comando.productoId()
            + "/galeria-"
            + GeneradorIdentificador.nuevo()
            + "."
            + extension;
    UrlFirmada urlFirmada = almacenDeImagenes.generarUrlDeSubida(objectKey, comando.contentType());
    return new SolicitudDeSubida(urlFirmada.url(), objectKey);
  }
}
