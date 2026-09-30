package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Un borrador como lo lista y lo edita el panel. Los dineros van como entero de pesos. */
public record BorradorRespuesta(
    UUID id,
    UUID publicacionId,
    UUID proveedorId,
    String titulo,
    String linea,
    String tipo,
    Long precioProveedor,
    Long precioVentaSugerido,
    TallasRespuesta tallas,
    Integer cantidadTonos,
    List<String> tonosNombrados,
    String material,
    List<String> caracteristicas,
    List<String> alertas,
    String estado,
    UUID productoId,
    String motivoRechazo,
    String extraccionCruda,
    Instant creadoEn) {

  public static BorradorRespuesta de(BorradorProducto b) {
    return new BorradorRespuesta(
        b.id(),
        b.publicacionId(),
        b.proveedorId(),
        b.titulo().orElse(null),
        b.linea().map(LineaCatalogo::name).orElse(null),
        b.tipo().name(),
        b.precioProveedor().map(BorradorRespuesta::entero).orElse(null),
        b.precioVentaSugerido().map(BorradorRespuesta::entero).orElse(null),
        new TallasRespuesta(
            b.tallas().tipo().name(), b.tallas().sirveHasta(), b.tallas().valores()),
        b.cantidadTonos().orElse(null),
        b.tonosNombrados(),
        b.material().orElse(null),
        b.caracteristicas(),
        b.alertas().stream().map(Enum::name).sorted().toList(),
        b.estado().name(),
        b.productoId().orElse(null),
        b.motivoRechazo().orElse(null),
        b.extraccionCruda(),
        b.creadoEn());
  }

  private static Long entero(Dinero dinero) {
    BigDecimal valor = dinero.valor();
    return valor.longValueExact();
  }

  public record TallasRespuesta(String tipo, String sirveHasta, List<String> valores) {}
}
