package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.Objects;

/**
 * Segundo paso: verifica contra el almacén real que el objeto llegó —no confía en que el navegador
 * terminó el {@code PUT}, igual que {@link ConfirmarImagenPrincipal}— y suma la imagen a la galería
 * del producto.
 *
 * <p><b>Aquí no se borra nada del bucket</b>, y esa es la diferencia con la principal. Allá cada
 * confirmación reemplaza a la anterior, así que dejar de limpiar el prefijo sería pagar para
 * siempre por un objeto que ya nadie sirve. Una galería, en cambio, acumula a propósito: borrar por
 * el prefijo {@code galeria-} se llevaría las imágenes hermanas que sí se están sirviendo. Quitar
 * una es un acto propio, {@link QuitarImagenDeGaleria}.
 *
 * <p>El precio de esa decisión es el mismo que allá pero al revés: una subida que se firma y nunca
 * se confirma deja un objeto que ninguna limpieza reclama. Se acepta a sabiendas — la alternativa
 * era arriesgar borrar fotos vivas — y por eso {@link SolicitarSubidaDeImagenDeGaleria} comprueba
 * el tope antes de firmar, para no invitar a subir lo que no va a caber.
 */
public final class AgregarImagenDeGaleria {

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public AgregarImagenDeGaleria(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public ImagenProducto ejecutar(AgregarImagenDeGaleriaComando comando) {
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
            TipoImagen.GALERIA,
            producto.siguienteOrdenDeGaleria(),
            url,
            url,
            comando.ancho(),
            comando.alto(),
            bytes,
            new HashContenido(comando.hash()),
            comando.altEs(),
            comando.altEn());

    // El agregado primero: es quien rechaza el duplicado y el tope, y si rechaza no se escribe.
    producto.agregarImagenGaleria(imagen);
    repositorioProductos.guardarImagenDeGaleria(producto.id(), imagen);
    return imagen;
  }
}
