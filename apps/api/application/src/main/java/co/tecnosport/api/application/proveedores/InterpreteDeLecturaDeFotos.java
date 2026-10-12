package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import java.util.Optional;

/**
 * Vuelve a leer la lectura de fotos que el borrador guardó tal cual llegó ({@code
 * BorradorProducto#lecturaDeFotos}), sin llamar a nadie.
 *
 * <p>Existe por las fotos que retratan todos los tonos juntos (12 de octubre de 2026). El borrador
 * solo guarda, por foto, el color de la que muestra uno solo; lo que el lector vio en una foto de
 * tres tonos se quedaba en el JSON. Leerlo al abrir el borrador sirve también para los que ya
 * estaban en revisión, sin migrar nada.
 */
public interface InterpreteDeLecturaDeFotos {

  /** El que no entiende nada: para quien no tiene lecturas guardadas que mirar. */
  InterpreteDeLecturaDeFotos NINGUNO = jsonCrudo -> Optional.empty();

  /**
   * @param jsonCrudo lo que devolvió el lector de fotos
   * @return la lectura, o vacío si el texto no se entiende: una lectura vieja o rota no impide
   *     revisar el borrador
   */
  Optional<LecturaDeFotos> interpretar(String jsonCrudo);
}
