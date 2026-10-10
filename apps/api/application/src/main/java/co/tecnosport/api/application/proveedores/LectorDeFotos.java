package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import java.util.Optional;

/**
 * Quien mira las fotos de una publicación y dice qué se ve en cada una: la referencia impresa, el
 * pie con tallas y SKU, los colores de lo que se vende y qué fotos son el mismo diseño.
 *
 * <p>Va aparte de {@link ExtractorDeProductos} porque falla aparte: una publicación cuyas fotos no
 * se pudieron leer sigue siendo un borrador, con el reparto de antes —todas las fotos para todos
 * sus productos—. Lo que devuelve tampoco se cree a ciegas: lo decide {@link
 * co.tecnosport.api.domain.proveedores.RepartoDeFotos}.
 */
public interface LectorDeFotos {

  /**
   * @return la lectura, o vacío si la lectura de fotos está apagada
   * @throws ExtraccionFallidaException si estaba encendida y no hubo forma de obtener una respuesta
   *     válida
   */
  Optional<LecturaDeFotos> leer(FotosParaLeer fotos);
}
