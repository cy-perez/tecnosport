package co.tecnosport.api.application.difusion;

import java.util.Objects;

/**
 * Una foto lista para mandarle a una red social: la URL que esa red va a descargar y las medidas
 * con las que se publicó.
 *
 * <p><b>Las medidas viajan porque la red las mira.</b> Instagram rechaza un contenedor cuya imagen
 * se salga de su rango de proporciones, y rechazarlo tarde —ya creado el contenedor— tumba el
 * carrusel entero por una sola foto. Con el ancho y el alto aquí, el adaptador puede dejar fuera la
 * que no entra y publicar las demás.
 *
 * <p>Cuál es ese rango no se decide aquí: es una regla de Meta, cambia cuando Meta quiera, y su
 * sitio es el adaptador. Lo mismo que hace {@code PublicacionEnRed} con el tope de caracteres del
 * pie.
 */
public record ImagenAPublicar(String url, int ancho, int alto) {

  public ImagenAPublicar {
    Objects.requireNonNull(url, "La URL de la imagen no puede ser nula.");
    if (url.isBlank()) {
      throw new IllegalArgumentException("La URL de la imagen no puede estar en blanco.");
    }
    if (ancho <= 0 || alto <= 0) {
      throw new IllegalArgumentException(
          "Una imagen a publicar tiene que traer sus medidas: " + ancho + "x" + alto + ".");
    }
  }

  /** Ancho partido por alto. 1 es cuadrada; por debajo de 1, vertical. */
  public double proporcion() {
    return (double) ancho / alto;
  }
}
