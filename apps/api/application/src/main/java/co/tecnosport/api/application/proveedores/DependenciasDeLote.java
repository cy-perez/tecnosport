package co.tecnosport.api.application.proveedores;

import java.util.List;
import java.util.UUID;

/**
 * Lo que cuelga de un lote antes de borrarlo.
 *
 * @param enCurso si está en la cola o a medio procesar
 * @param productos los productos que nacieron de un borrador aprobado de este lote —no los que una
 *     renovación de este lote actualizó, que son de otro—
 * @param archivos el ZIP de la exportación y las fotos de sus mensajes en el bucket privado
 */
public record DependenciasDeLote(boolean enCurso, List<UUID> productos, List<String> archivos) {

  public DependenciasDeLote {
    productos = productos == null ? List.of() : List.copyOf(productos);
    archivos = archivos == null ? List.of() : List.copyOf(archivos);
  }
}
