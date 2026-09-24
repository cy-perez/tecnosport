package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Saca una imagen de la galería y borra su objeto del bucket.
 *
 * <p><b>Primero la fila, después el objeto</b>, el mismo orden que {@link ConfirmarImagenPrincipal}
 * y el contrario al de {@code EliminarSetRotacion}. Si se borrara el objeto primero y el guardado
 * fallara, la ficha quedaría viva apuntando a una imagen que ya no existe. Al revés, lo peor que
 * pasa es un objeto sin reclamar, que no se ve desde fuera.
 *
 * <p><b>Y se borran por key exacta las de todas sus variantes, no por prefijo.</b> Todas las
 * imágenes de la galería de un producto comparten el prefijo {@code galeria-}: limpiarlo se
 * llevaría las hermanas que siguen publicadas. Es justo lo contrario de la principal, donde el
 * prefijo entero <em>es</em> lo que sobra.
 *
 * <p>Una URL que no sea de este almacén —el catálogo sembrado de {@code local} y {@code dev} trae
 * imágenes de picsum.photos— saca la fila y no intenta borrar nada. Quitar tiene que funcionar
 * igual ahí: lo que sobra es la imagen en la ficha, no el objeto.
 */
public final class QuitarImagenDeGaleria {

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public QuitarImagenDeGaleria(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public ImagenDeGaleriaQuitada ejecutar(QuitarImagenDeGaleriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    // Lanza si esa imagen no es de la galería de este producto, que es la guarda que impide que un
    // id suelto borre la foto de otro.
    ImagenProducto quitada = producto.quitarImagenGaleria(comando.imagenId());
    // Si no había fila que borrar, el agregado que se leyó ya estaba obsoleto: otra petición
    // quitó esa misma imagen mientras tanto. Seguir hasta el bucket haría que el controlador
    // registrara el aviso de "salió de la galería sin borrar ningún objeto", que está escrito para
    // el día que la URL pública cambie por un CDN. Un aviso que suena por dos motivos distintos no
    // sirve para ninguno.
    if (!repositorioProductos.eliminarImagenDeGaleria(producto.id(), quitada.id())) {
      return new ImagenDeGaleriaQuitada(quitada, false, false);
    }

    // Todas las claves de esta imagen, no solo una. Desde `ADR-0057` una imagen no tiene una URL
    // sino varias —hasta cinco anchos más el JPEG de vista previa— y este caso de uso se había
    // quedado en el mundo anterior: `quitada.url()` es la variante mayor, así que cada imagen
    // quitada dejaba las otras pagándose todos los meses. Y aquí no se puede limpiar por prefijo
    // para recuperarlas después, a propósito: `galeria-` lo comparten las hermanas que siguen
    // publicadas, y eso está escrito arriba. O se borran por key exacta, todas, o no las borra
    // nadie — `npm run huerfanos` informa, no borra.
    List<String> claves = new ArrayList<>();
    for (VarianteDeImagen variante : quitada.variantes()) {
      almacenDeImagenes.objectKeyDe(variante.url()).ifPresent(claves::add);
    }
    quitada.urlVistaPrevia().flatMap(almacenDeImagenes::objectKeyDe).ifPresent(claves::add);

    if (claves.isEmpty()) {
      return new ImagenDeGaleriaQuitada(quitada, false, false);
    }

    try {
      // Se cuenta como borrada si al menos un objeto se fue; el aviso que importa —y que el
      // controlador registra— es el de la limpieza fallida.
      boolean algunoBorrado = false;
      for (String clave : claves) {
        algunoBorrado |= almacenDeImagenes.eliminar(clave);
      }
      return new ImagenDeGaleriaQuitada(quitada, algunoBorrado, false);
    } catch (RuntimeException e) {
      // La fila ya no está y la ficha ya no la muestra: propagar esto sería reportar como fallida
      // una operación que funcionó. Lo que queda es un objeto sin reclamar, y eso se reporta para
      // que quede en el registro en vez de desaparecer.
      return new ImagenDeGaleriaQuitada(quitada, false, true);
    }
  }
}
