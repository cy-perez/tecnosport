package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un borrador de tecnología como lo revisa el panel. Los dineros van como entero de pesos.
 *
 * @param productoId el producto que ya vende este modelo, si el borrador es de configuraciones
 *     nuevas de uno existente; o el que nació al aprobarlo
 */
public record BorradorTecnologiaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID proveedorId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idModelo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
    String marcaSugerida,
    String categoriaSugerida,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String descripcion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> paleta,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ConfiguracionRespuesta> configuraciones,
    UUID productoId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String estado,
    String motivoRechazo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant vistoEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant creadoEn) {

  public static BorradorTecnologiaRespuesta de(BorradorTecnologia b) {
    return new BorradorTecnologiaRespuesta(
        b.id(),
        b.proveedorId(),
        b.modelo().idModelo(),
        b.modelo().titulo(),
        b.modelo().marca(),
        b.modelo().categoria(),
        b.modelo().descripcion(),
        b.modelo().paleta(),
        b.configuraciones().stream().map(ConfiguracionRespuesta::de).toList(),
        b.productoId().orElse(null),
        b.estado().name(),
        b.motivoRechazo().orElse(null),
        b.vistoEn(),
        b.creadoEn());
  }

  public record ConfiguracionRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sku,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
      String ram,
      String almacenamiento,
      String sim,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long costoProveedor,
      Long precioMercado,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> coloresSugeridos,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> coloresElegidos,
      Long precioVenta) {

    static ConfiguracionRespuesta de(ConfiguracionTecnologia c) {
      return new ConfiguracionRespuesta(
          c.sku(),
          c.titulo(),
          c.ram(),
          c.almacenamiento(),
          c.sim(),
          entero(c.costoProveedor()),
          c.precioMercado() == null ? null : entero(c.precioMercado()),
          c.coloresSugeridos(),
          c.coloresElegidos(),
          c.precioVenta() == null ? null : entero(c.precioVenta()));
    }
  }

  private static long entero(Dinero dinero) {
    return dinero.valor().longValueExact();
  }
}
