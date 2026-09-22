package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaDuplicadaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // El prefijo completo, con 'galeria-', y no solo el del producto. Esta guarda nació copiada de
    // ConfirmarImagenPrincipal, donde bastaba porque había un solo prefijo por producto. Con dos,
    // aceptar una key de 'principal-' como imagen de galería tiene una consecuencia concreta: el
    // siguiente reemplazo de la imagen principal limpia ese prefijo entero y borra el objeto que la
    // galería está sirviendo. Una foto rota en una ficha publicada, causada por el propio sistema y
    // sin una línea de error en ningún sitio.
    List<VarianteSubida> variantes =
        Objects.requireNonNullElse(comando.variantes(), List.<VarianteSubida>of());
    if (variantes.isEmpty()) {
      throw new IllegalArgumentException("Hay que confirmar al menos una variante de la imagen.");
    }

    String prefijoEsperado = ClavesDeGaleria.prefijoDe(comando.productoId());
    List<String> claves =
        new ArrayList<>(variantes.stream().map(VarianteSubida::objectKey).toList());
    if (comando.objectKeyVistaPrevia() != null && !comando.objectKeyVistaPrevia().isBlank()) {
      claves.add(comando.objectKeyVistaPrevia().trim());
    }

    Map<String, Long> bytesPorClave = new LinkedHashMap<>();
    for (String clave : claves) {
      if (clave == null || !clave.startsWith(prefijoEsperado)) {
        throw new IllegalArgumentException(
            "El objeto '"
                + clave
                + "' no es una imagen de galería del producto "
                + comando.productoId()
                + ".");
      }
      bytesPorClave.put(
          clave,
          almacenDeImagenes
              .tamanoBytes(clave)
              .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(clave)));
    }

    List<VarianteDeImagen> variantesDeImagen =
        variantes.stream()
            .map(
                v ->
                    new VarianteDeImagen(
                        v.ancho(),
                        almacenDeImagenes.urlPublica(v.objectKey()),
                        bytesPorClave.get(v.objectKey())))
            .toList();

    // La de la variante mayor, que es la que el agregado toma como base y la que la galería ya
    // comparaba antes de que existieran las variantes.
    String url =
        variantesDeImagen.stream()
            .max(Comparator.comparingInt(VarianteDeImagen::ancho))
            .orElseThrow()
            .url();

    // Dos filas apuntando al mismo objeto romperían el borrado: quitar una se lleva el archivo por
    // la key exacta y deja a la hermana rota. El rechazo por hash no cubre esto —el hash lo manda
    // el cliente—, así que la unicidad que de verdad sostiene el borrado se comprueba aquí.
    boolean mismoObjeto = producto.galeria().stream().anyMatch(i -> i.url().equals(url));
    if (mismoObjeto) {
      throw new ImagenDeGaleriaDuplicadaException(
          "El objeto '" + url + "' ya está en la galería de este producto.");
    }
    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.GALERIA,
            producto.siguienteOrdenDeGaleria(),
            variantesDeImagen,
            comando.objectKeyVistaPrevia() == null || comando.objectKeyVistaPrevia().isBlank()
                ? null
                : almacenDeImagenes.urlPublica(comando.objectKeyVistaPrevia().trim()),
            comando.alto(),
            new HashContenido(comando.hash()),
            comando.altEs(),
            comando.altEn());

    // El agregado primero: es quien rechaza el duplicado y el tope, y si rechaza no se escribe.
    producto.agregarImagenGaleria(imagen);
    repositorioProductos.guardarImagenDeGaleria(producto.id(), imagen);
    return imagen;
  }
}
