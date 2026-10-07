package co.tecnosport.api.application.difusion;

import java.util.Optional;
import java.util.UUID;

/**
 * Encaja una foto en la proporción que una red exige, sin recortar el producto.
 *
 * <p><b>Ensancha el lienzo en vez de cortar, y esa es la decisión.</b> Instagram no admite nada más
 * alto que 4:5, y las fotos del proveedor llegan hasta 0,62 — una prenda de cuerpo entero. Llevar
 * esa foto a 0,8 cortando son 22 % del alto, o sea el ruedo o la pretina del pantalón. Añadiendo
 * ancho no se pierde nada de lo que se vende; lo que aparece son dos franjas a los lados, del color
 * que ya tiene el borde de la foto.
 *
 * <p>Es un puerto y no una utilidad porque el trabajo es de infraestructura de punta a punta: abrir
 * los bytes, redibujarlos y dejar el resultado en el bucket para que Meta pueda descargarlo.
 */
public interface AjustadorDeImagenes {

  /**
   * La misma foto con la proporción pedida, publicada en el bucket y lista para que la red la
   * descargue. Vacío si no se pudo —los bytes no están, o no se pueden abrir—, y entonces quien
   * llama se queda con la original y deja que la red decida.
   *
   * <p>La foto que ya tiene esa proporción se devuelve tal cual, sin tocar el bucket.
   *
   * @param proporcionObjetivo ancho partido por alto, mayor que cero
   */
  Optional<ImagenAPublicar> ajustarA(
      UUID productoId, ImagenAPublicar imagen, double proporcionObjetivo);
}
