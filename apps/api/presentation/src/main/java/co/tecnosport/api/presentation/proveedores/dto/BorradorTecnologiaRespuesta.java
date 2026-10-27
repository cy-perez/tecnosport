package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.tecnologia.FotosFirmadasDeBorradorTecnologia;
import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.SugerenciaDePrecioTecnologia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un borrador de tecnología como lo revisa el panel. Los dineros van como entero de pesos.
 *
 * @param productoId el producto que ya vende este modelo, si el borrador es de configuraciones
 *     nuevas de uno existente; o el que nació al aprobarlo
 * @param cantidadDeFotos cuántas fotos tiene, también en la bandeja
 * @param fotosQueFaltan cuántas le faltan para aprobarse; cero en uno de configuraciones nuevas
 * @param fotos las fotos con su URL firmada, en el orden en que se publican; vacía en la bandeja,
 *     que no las firma (ADR-0079)
 * @param descripcion la ficha que se publicará al aprobar: la editada a mano si la hay
 * @param descripcionDeLaSkill la que preparó la skill, aunque se haya editado
 * @param fichaEditada si {@code descripcion} es una edición a mano
 * @param fichaNuevaDeLaSkill si una lista trajo otra ficha de la skill después de la edición
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
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant creadoEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int cantidadDeFotos,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int fotosQueFaltan,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FotoDeBorradorTecnologiaRespuesta> fotos,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String descripcionDeLaSkill,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean fichaEditada,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean fichaNuevaDeLaSkill) {

  /**
   * Cómo se calculan los dos precios sugeridos de cada configuración (ADR-0095): el de contado con
   * su tope de ganancia, y el publicado con la comisión de la pasarela.
   */
  public record Precios(SugerenciaDePrecioTecnologia sugerencia, ComisionDePasarela comision) {}

  /** Para la bandeja: sin firmar las fotos. */
  public static BorradorTecnologiaRespuesta de(BorradorTecnologia b, Precios precios) {
    return de(b, List.of(), precios);
  }

  public static BorradorTecnologiaRespuesta de(
      BorradorTecnologia b,
      List<FotosFirmadasDeBorradorTecnologia.FotoVista> fotos,
      Precios precios) {
    return new BorradorTecnologiaRespuesta(
        b.id(),
        b.proveedorId(),
        b.modelo().idModelo(),
        b.modelo().titulo(),
        b.modelo().marca(),
        b.modelo().categoria(),
        b.descripcion(),
        b.modelo().paleta(),
        b.configuraciones().stream().map(c -> ConfiguracionRespuesta.de(c, precios)).toList(),
        b.productoId().orElse(null),
        b.estado().name(),
        b.motivoRechazo().orElse(null),
        b.vistoEn(),
        b.creadoEn(),
        b.fotos().size(),
        b.fotosQueFaltan(),
        fotos.stream().map(FotoDeBorradorTecnologiaRespuesta::de).toList(),
        b.modelo().descripcion(),
        b.descripcionEditada().isPresent(),
        b.fichaNuevaDeLaSkill());
  }

  /** Una foto del borrador; la primera es la principal. */
  public record FotoDeBorradorTecnologiaRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ancho,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alto,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hash,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEs,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEn) {

    static FotoDeBorradorTecnologiaRespuesta de(FotosFirmadasDeBorradorTecnologia.FotoVista f) {
      return new FotoDeBorradorTecnologiaRespuesta(
          f.id(), f.url(), f.ancho(), f.alto(), f.hash(), f.altEs(), f.altEn());
    }
  }

  /**
   * @param precioMercado el que investigó la skill; nulo si no lo encontró
   * @param precioContadoSugerido {@code min(precioMercado, costo + ganancia máxima)} (ADR-0095,
   *     decisión 8); nulo sin precio de mercado. Es el que se precarga como {@code precioVenta}
   * @param precioPublicadoSugerido el que se anunciaría con ese contado, con la comisión de la
   *     pasarela encima. Solo para mirar; nulo sin contado sugerido
   * @param precioVenta el <b>precio de contado</b> que fijó quien revisa; nulo hasta entonces
   */
  public record ConfiguracionRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sku,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
      String ram,
      String almacenamiento,
      String sim,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long costoProveedor,
      Long precioMercado,
      Long precioContadoSugerido,
      Long precioPublicadoSugerido,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> coloresSugeridos,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> coloresElegidos,
      Long precioVenta) {

    static ConfiguracionRespuesta de(ConfiguracionTecnologia c, Precios precios) {
      Dinero contado = precios.sugerencia().contadoSugerido(c);
      return new ConfiguracionRespuesta(
          c.sku(),
          c.titulo(),
          c.ram(),
          c.almacenamiento(),
          c.sim(),
          entero(c.costoProveedor()),
          c.precioMercado() == null ? null : entero(c.precioMercado()),
          contado == null ? null : entero(contado),
          contado == null ? null : entero(precios.comision().publicadoDesde(contado)),
          c.coloresSugeridos(),
          c.coloresElegidos(),
          c.precioVenta() == null ? null : entero(c.precioVenta()));
    }
  }

  private static long entero(Dinero dinero) {
    return dinero.valor().longValueExact();
  }
}
