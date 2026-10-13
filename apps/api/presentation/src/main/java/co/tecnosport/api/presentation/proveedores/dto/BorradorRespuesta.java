package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un borrador como lo lista y lo edita el panel. Los dineros van como entero de pesos.
 *
 * @param tallasPorTono las tallas de cada tono cuando el mensaje no las tiene todas en todos; vacía
 *     si las tiene
 * @param preciosAdicionales los del anuncio que no son de este producto: la gorra, el combo, la
 *     promoción por cantidad, y en un set la prenda sola. Solo para mirar
 * @param set con qué prenda se armó el set y de dónde salió su costo; nulo si el borrador es la
 *     prenda sola
 */
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
    String descripcion,
    String altEn,
    List<String> alertas,
    String estado,
    UUID productoId,
    String motivoRechazo,
    String extraccionCruda,
    Instant creadoEn,
    List<TallasDeTonoRespuesta> tallasPorTono,
    List<PrecioAdicionalRespuesta> preciosAdicionales,
    SetRespuesta set,
    /** Cuántas tallas más arriba las nombra el proveedor (ADR-0085); 0 si igual. */
    int ajusteDeTalla,
    /**
     * Cómo se le pide al proveedor cada talla, con el paso del tipo de prenda; vacía sin ajuste. La
     * del proveedor es nula si no se puede contar.
     */
    List<TallaPedidaRespuesta> tallasPedidas) {

  /**
   * @param talla la que se vende
   * @param delProveedor la que se le pide al proveedor; nula si no se puede contar
   */
  public record TallaPedidaRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String talla, String delProveedor) {}

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
        b.descripcion().orElse(null),
        b.altEn().orElse(null),
        b.alertas().stream().map(Enum::name).sorted().toList(),
        b.estado().name(),
        b.productoId().orElse(null),
        b.motivoRechazo().orElse(null),
        b.extraccionCruda(),
        b.creadoEn(),
        b.tallasPorTono().tonos().stream()
            .map(t -> new TallasDeTonoRespuesta(t.tono(), t.tallas()))
            .toList(),
        b.preciosAdicionales().stream()
            .map(
                p ->
                    new PrecioAdicionalRespuesta(
                        p.concepto(),
                        entero(p.precio()),
                        p.claseOpcional().map(Enum::name).orElse(null),
                        p.prendaOpcional().map(Enum::name).orElse(null)))
            .toList(),
        b.set()
            .map(s -> new SetRespuesta(s.acompanante().name(), s.origenDelCosto().name()))
            .orElse(null),
        b.ajusteDeTalla().tallas(),
        b.tallasPedidas().stream()
            .map(t -> new TallaPedidaRespuesta(t.talla(), t.delProveedor()))
            .toList());
  }

  private static Long entero(Dinero dinero) {
    BigDecimal valor = dinero.valor();
    return valor.longValueExact();
  }

  public record TallasRespuesta(String tipo, String sirveHasta, List<String> valores) {}

  public record TallasDeTonoRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tono,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tallas) {}

  /**
   * @param clase {@code ACOMPANANTE}, {@code COMBO}, {@code PROMOCION} o {@code PRENDA_SOLA}; nula
   *     en los guardados antes del 12 de octubre de 2026
   * @param prenda la que acompaña: {@code BLUSA}, {@code GORRA} u {@code OTRA}; nula si no hay
   */
  public record PrecioAdicionalRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String concepto,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precio,
      String clase,
      String prenda) {}

  /**
   * @param acompanante {@code BLUSA} o {@code GORRA}
   * @param origenDelCosto {@code ANUNCIADO} si el proveedor dio el precio del set, {@code
   *     SUMA_DE_PRENDAS} si se sumaron
   */
  public record SetRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String acompanante,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String origenDelCosto) {}
}
