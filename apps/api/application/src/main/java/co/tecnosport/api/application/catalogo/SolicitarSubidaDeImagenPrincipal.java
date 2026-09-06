package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Map;
import java.util.Objects;

/**
 * Primer paso de la subida de la imagen principal: valida el producto y el tipo de contenido, y
 * pide al almacén una URL firmada para un objeto nuevo. La confirmación (verificar que el objeto
 * llegó y guardar la {@code ImagenProducto}) es {@link ConfirmarImagenPrincipal}, un paso aparte.
 */
public final class SolicitarSubidaDeImagenPrincipal {

  private static final Map<String, String> EXTENSIONES_SOPORTADAS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public SolicitarSubidaDeImagenPrincipal(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public SolicitudDeSubida ejecutar(SolicitarSubidaDeImagenPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    repositorioProductos
        .buscarPorId(comando.productoId())
        .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    String extension = EXTENSIONES_SOPORTADAS.get(comando.contentType());
    if (extension == null) {
      throw new IllegalArgumentException(
          "Tipo de contenido no soportado para imagen principal: " + comando.contentType());
    }

    String objectKey =
        "productos/"
            + comando.productoId()
            + "/principal-"
            + GeneradorIdentificador.nuevo()
            + "."
            + extension;
    UrlFirmada urlFirmada = almacenDeImagenes.generarUrlDeSubida(objectKey, comando.contentType());
    return new SolicitudDeSubida(urlFirmada.url(), objectKey);
  }
}
