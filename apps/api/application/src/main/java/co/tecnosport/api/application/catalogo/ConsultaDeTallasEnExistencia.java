package co.tecnosport.api.application.catalogo;

import java.util.List;

/**
 * Las tallas de ropa y de calzado que tienen al menos una unidad que se puede comprar ahora.
 *
 * <p>Puerto aparte de {@link RepositorioProductos} y no un método más de aquel: es una lectura de
 * la vitrina que cruza catálogo e inventario, y en el repositorio obligaría a implementarla a los
 * seis dobles de prueba que no la usan.
 */
public interface ConsultaDeTallasEnExistencia {

  /** Solo de las líneas con talla —ropa y calzado— y solo de los ejes de talla. */
  List<TallaEnExistencia> tallasEnExistencia();
}
