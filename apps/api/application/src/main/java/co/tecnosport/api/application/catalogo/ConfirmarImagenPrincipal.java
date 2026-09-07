package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.Objects;
import java.util.Set;

/**
 * Segundo paso: verifica contra el almacén real que el objeto llegó (no confía en que el navegador
 * terminó el `PUT`), arma la {@code ImagenProducto} y reemplaza la principal del producto. {@code
 * urlWebp} apunta al mismo objeto que {@code url} — sin conversión de formato en este paso, eso lo
 * hace el asistente de captura en Fase 5.
 *
 * <p><strong>La imagen anterior se borra del bucket</strong>, o cada reemplazo dejaría pagando un
 * objeto que ya nadie sirve. Se borra por prefijo —{@code productos/{id}/principal-}— salvo la key
 * recién subida, así que se lleva también lo que quedó de subidas que nunca se confirmaron.
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
            new HashContenido(comando.hash()),
            comando.altEs(),
            comando.altEn());

    producto.asignarImagenPrincipal(imagen);
    repositorioProductos.guardarImagenPrincipal(producto.id(), imagen);

    try {
      int borrados =
          almacenDeImagenes.eliminarPorPrefijo(
              prefijoEsperado + "principal-", Set.of(comando.objectKey()));
      return new ConfirmacionDeImagenPrincipal(imagen, borrados, false);
    } catch (RuntimeException e) {
      // La imagen ya está guardada y la ficha ya la muestra: propagar esto sería reportar como
      // fallida una operación que funcionó. Lo que sí queda es basura en el bucket, y de eso se
      // encarga el siguiente reemplazo, que borra el prefijo entero menos la key vigente.
      return new ConfirmacionDeImagenPrincipal(imagen, 0, true);
    }
  }
}
