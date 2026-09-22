package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Segundo paso: verifica contra el almacén real que el objeto llegó (no confía en que el navegador
 * terminó el `PUT`), arma la {@code ImagenProducto} y reemplaza la principal del producto.
 *
 * <p><strong>Se verifica cada objeto, no solo el primero.</strong> Una variante que no llegó al
 * bucket y se guarda igual sale en el {@code srcset} y el navegador la pide: una foto rota en una
 * ficha publicada, elegida por el propio navegador entre las que le ofrecimos. Lo mismo vale para
 * el JPEG de la vista previa, que además nadie vería fallar desde el sitio — se rompe en WhatsApp,
 * lejos de aquí.
 *
 * <p><strong>La imagen anterior se borra del bucket</strong>, o cada reemplazo dejaría pagando un
 * objeto que ya nadie sirve. Se borra por prefijo —{@code productos/{id}/principal-}— salvo la key
 * recién subidas —todas, no solo la mayor—, así que se lleva también lo que quedó de subidas que
 * nunca se confirmaron.
 *
 * <p><strong>Y se borra después de guardar, al revés que en {@code EliminarSetRotacion}</strong>.
 * Un set se va entero, así que allá conviene borrar los objetos primero. Aquí la fila se reemplaza:
 * si se borrara antes y el guardado fallara, el producto quedaría apuntando a un objeto que ya no
 * existe — una imagen rota en una ficha viva. El precio de este orden es que una limpieza que falla
 * deja objetos sin reclamar; se reporta en {@code ConfirmacionDeImagenPrincipal} en vez de tumbar
 * una confirmación que ya se guardó, y se cura sola: el siguiente reemplazo de ese producto borra
 * todo lo que haya quedado bajo el prefijo.
 */
public final class ConfirmarImagenPrincipal {

  private final RepositorioProductos repositorioProductos;
  private final AlmacenDeImagenes almacenDeImagenes;

  public ConfirmarImagenPrincipal(
      RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  public ConfirmacionDeImagenPrincipal ejecutar(ConfirmarImagenPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    List<VarianteSubida> variantes =
        Objects.requireNonNullElse(comando.variantes(), List.<VarianteSubida>of());
    if (variantes.isEmpty()) {
      throw new IllegalArgumentException("Hay que confirmar al menos una variante de la imagen.");
    }

    String prefijoEsperado = "productos/" + comando.productoId() + "/";
    List<String> claves =
        new ArrayList<>(variantes.stream().map(VarianteSubida::objectKey).toList());
    if (comando.objectKeyVistaPrevia() != null && !comando.objectKeyVistaPrevia().isBlank()) {
      claves.add(comando.objectKeyVistaPrevia().trim());
    }
    // El tamaño se pregunta una sola vez por objeto y se guarda: cada consulta es una llamada a
    // Cloud Storage, y preguntarlo otra vez al armar las variantes costaría el doble de viajes sin
    // enterarse de nada nuevo.
    Map<String, Long> bytesPorClave = new LinkedHashMap<>();
    for (String clave : claves) {
      if (clave == null || !clave.startsWith(prefijoEsperado)) {
        throw new IllegalArgumentException(
            "El objeto '" + clave + "' no pertenece al producto " + comando.productoId() + ".");
      }
      // Que exista de verdad, una por una. El almacén es la única fuente que no miente aquí: el
      // cliente puede reportar una key que nunca subió, y hasta hacerlo sin mala intención si un
      // `PUT` falló y no lo miró.
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

    String urlVistaPrevia =
        comando.objectKeyVistaPrevia() == null || comando.objectKeyVistaPrevia().isBlank()
            ? null
            : almacenDeImagenes.urlPublica(comando.objectKeyVistaPrevia().trim());

    ImagenProducto imagen =
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            variantesDeImagen,
            urlVistaPrevia,
            comando.alto(),
            new HashContenido(comando.hash()),
            comando.altEs(),
            comando.altEn());

    producto.asignarImagenPrincipal(imagen);
    repositorioProductos.guardarImagenPrincipal(producto.id(), imagen);

    try {
      // Todas las claves recién confirmadas, no solo la primera: la limpieza borra el prefijo
      // entero, así que una variante que no estuviera en esta lista se borraría a sí misma justo
      // después de guardarse.
      int borrados =
          almacenDeImagenes.eliminarPorPrefijo(prefijoEsperado + "principal-", Set.copyOf(claves));
      return new ConfirmacionDeImagenPrincipal(imagen, borrados, false);
    } catch (RuntimeException e) {
      // La imagen ya está guardada y la ficha ya la muestra: propagar esto sería reportar como
      // fallida una operación que funcionó. Lo que sí queda es basura en el bucket, y de eso se
      // encarga el siguiente reemplazo, que borra el prefijo entero menos la key vigente.
      return new ConfirmacionDeImagenPrincipal(imagen, 0, true);
    }
  }
}
