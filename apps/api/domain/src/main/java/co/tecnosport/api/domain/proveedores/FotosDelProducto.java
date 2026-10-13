package co.tecnosport.api.domain.proveedores;

import java.util.List;
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
 * @param diseno la etiqueta de diseño que el lector le dio a sus fotos, en un álbum: dos borradores
 *     de la misma publicación con la misma etiqueta son, posiblemente, el mismo diseño en otro
 *     color. Nula fuera de un álbum o si el lector no dio ninguna
 * @param referenciasDelAlbum los SKU impresos de un álbum, uno por prenda (13 de octubre de 2026):
 *     con ellos se reconoce el álbum que el proveedor vuelve a publicar con los mismos SKU. Vacía
 *     fuera de un álbum. No se guarda en el borrador: el panel los vuelve a leer de la lectura
 */
public record FotosDelProducto(
    Set<UUID> ajenas,
    Map<UUID, String> tonosSugeridos,
    String lecturaCruda,
    String diseno,
    List<String> referenciasDelAlbum) {

  public FotosDelProducto {
    ajenas = ajenas == null ? Set.of() : Set.copyOf(ajenas);
    tonosSugeridos = tonosSugeridos == null ? Map.of() : Map.copyOf(tonosSugeridos);
    diseno = BorradorProducto.etiquetaDeDiseno(diseno);
    referenciasDelAlbum =
        referenciasDelAlbum == null ? List.of() : List.copyOf(referenciasDelAlbum);
  }

  public FotosDelProducto(
      Set<UUID> ajenas, Map<UUID, String> tonosSugeridos, String lecturaCruda, String diseno) {
    this(ajenas, tonosSugeridos, lecturaCruda, diseno, List.of());
  }

  public FotosDelProducto(Set<UUID> ajenas, Map<UUID, String> tonosSugeridos, String lecturaCruda) {
    this(ajenas, tonosSugeridos, lecturaCruda, null);
  }

  /** Todas las fotos de la publicación, sin sugerencias: lo de antes de leer las fotos. */
  public static FotosDelProducto todas() {
    return new FotosDelProducto(Set.of(), Map.of(), null);
  }
}
