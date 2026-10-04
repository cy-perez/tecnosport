package co.tecnosport.api.application.proveedores;

import java.util.List;
import java.util.UUID;

/**
 * Lo que cuelga de un lote antes de borrarlo. Si está en curso lo dice el propio lote ({@code
 * LoteIngesta.estaAbierto()}), no esto.
 *
 * @param productos los productos que nacieron de un borrador aprobado de este lote —no los que una
 *     renovación de este lote actualizó, que son de otro—
 * @param archivos el ZIP de la exportación —si ningún otro lote lo nombra— y las fotos de sus
 *     mensajes en el bucket privado
 */
public record DependenciasDeLote(List<UUID> productos, List<String> archivos) {

  public DependenciasDeLote {
    productos = productos == null ? List.of() : List.copyOf(productos);
    archivos = archivos == null ? List.of() : List.copyOf(archivos);
  }
}
