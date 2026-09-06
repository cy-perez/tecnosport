package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import java.util.Objects;

/**
 * Segundo paso: verifica contra el almacén real que el objeto llegó (no confía en que el navegador
 * terminó el `PUT`), arma la {@code ImagenProducto} y reemplaza la principal del producto. {@code
 * urlWebp} apunta al mismo objeto que {@code url} — sin conversión de formato en este paso, eso lo
 * hace el asistente de captura en Fase 5.
 */
public final class ConfirmarImagenPrincipal {

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public ConfirmarImagenPrincipal(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public ImagenProducto ejecutar(ConfirmarImagenPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    String prefijoEsperado = "productos/" + comando.productoId() + "/";
    if (!comando.objectKey().startsWith(prefijoEsperado)) {
      throw new IllegalArgumentException(
          "El objeto '"
              + comando.objectKey()
              + "' no pertenece al producto "
              + comando.productoId()
              + ".");
    }

    long bytes =
        almacenDeImagenes
            .tamanoBytes(comando.objectKey())
            .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(comando.objectKey()));

    String url = almacenDeImagenes.urlPublica(comando.objectKey());
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            url,
            url,
            comando.ancho(),
            comando.alto(),
            bytes,
            comando.objectKey(),
            comando.altEs(),
            comando.altEn());

    producto.asignarImagenPrincipal(imagen);
    repositorioProductos.guardarImagenPrincipal(producto.id(), imagen);
    return imagen;
  }
}
