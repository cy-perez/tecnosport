package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Optional;

/**
 * Dibuja la portada de marca que abre el carrusel de una publicación: la foto principal del
 * producto con la banda grafito, el logo, el nombre y la franja ámbar ({@code ADR-0102}).
 *
 * <p><b>No lleva el precio, y es una decisión.</b> Una imagen publicada no se edita: si el precio
 * cambia, el post seguiría anunciando el viejo, y el precio anunciado obliga (Ley 1480). El precio
 * va en el pie, que sí se puede corregir desde Meta.
 *
 * <p>Es un puerto por lo mismo que {@link AjustadorDeImagenes}: abrir los bytes, dibujar y dejar el
 * resultado en el bucket es infraestructura de punta a punta.
 */
public interface CompositorDePortada {

  /**
   * La portada publicada en el bucket, lista para que la red la descargue. Vacío si no se pudo —la
   * foto no está en el bucket o no se puede abrir—, y entonces la publicación sale sin portada: una
   * portada que falla no es motivo para no publicar.
   *
   * @param principal la foto principal en un formato que se pueda abrir, la misma que encabezaría
   *     el carrusel sin portada
   */
  Optional<ImagenAPublicar> componer(Producto producto, ImagenAPublicar principal);
}
