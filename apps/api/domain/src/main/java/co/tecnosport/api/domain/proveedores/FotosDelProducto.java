package co.tecnosport.api.domain.proveedores;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cuáles fotos de la publicación son de un producto, según el reparto ({@link RepartoDeFotos}).
 *
 * @param ajenas las fotos de la publicación que son de otro producto, por mensaje: el borrador nace
 *     con ellas descartadas
 * @param tonosSugeridos el color de cada foto que muestra un solo color a la venta, por mensaje
 * @param lecturaCruda el JSON de la lectura de fotos, tal cual llegó; nulo si no se leyeron
 */
public record FotosDelProducto(
    Set<UUID> ajenas, Map<UUID, String> tonosSugeridos, String lecturaCruda) {

  public FotosDelProducto {
    ajenas = ajenas == null ? Set.of() : Set.copyOf(ajenas);
    tonosSugeridos = tonosSugeridos == null ? Map.of() : Map.copyOf(tonosSugeridos);
  }

  /** Todas las fotos de la publicación, sin sugerencias: lo de antes de leer las fotos. */
  public static FotosDelProducto todas() {
    return new FotosDelProducto(Set.of(), Map.of(), null);
  }
}
