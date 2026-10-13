package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.VerBorrador;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * El borrador con lo que hace falta para revisarlo: los textos originales del proveedor y sus fotos
 * con URL firmada de lectura, que caduca en minutos porque el bucket es privado.
 *
 * @param prendasDelAlbum si la publicación es un álbum, una prenda por SKU impreso con sus fotos y
 *     las tallas de su pie: lo que el panel propone al aprobar (13 de octubre de 2026). Vacía si no
 */
public record BorradorDetalleRespuesta(
    BorradorRespuesta borrador,
    List<String> textos,
    List<FotoRespuesta> fotos,
    List<HermanoRespuesta> hermanos,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PrendaDelAlbumRespuesta> prendasDelAlbum) {

  public static BorradorDetalleRespuesta de(VerBorrador.DetalleDeBorrador detalle) {
    return new BorradorDetalleRespuesta(
        BorradorRespuesta.de(detalle.borrador()),
        detalle.textos(),
        detalle.fotos().stream().map(FotoRespuesta::de).toList(),
        detalle.hermanos().stream()
            .map(h -> new HermanoRespuesta(h.id(), h.titulo(), h.mismoDiseno()))
            .toList(),
        detalle.prendasDelAlbum().stream()
            .map(p -> new PrendaDelAlbumRespuesta(p.referencia(), p.tallas(), p.fotos()))
            .toList());
  }

  /**
   * Una prenda de un álbum: un diseño con su SKU.
   *
   * @param referencia el SKU del pie de sus fotos, que la aprobación deja en sus variantes
   * @param tallas las de su pie; vacía si no las dice
   * @param fotos las suyas, por {@code mensajeId} y en orden
   */
  public record PrendaDelAlbumRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String referencia,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tallas,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> fotos) {}

  /**
   * Otro borrador en revisión de la misma publicación: a donde el panel deja mover una foto, y con
   * el que deja unirlo.
   *
   * @param titulo nulo si la extracción no le dio ninguno
   * @param mismoDiseno la lectura de fotos vio en él el mismo diseño: posiblemente es el mismo
   *     producto en otro color
   */
  public record HermanoRespuesta(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      String titulo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean mismoDiseno) {}

  /**
   * @param mensajeId el id de la foto, venga del proveedor o del panel: es el que se manda al
   *     aprobar y al quitarla
   * @param url nula cuando la exportación omitió el archivo: la foto existió y no está
   * @param origen {@code PROVEEDOR} o {@code PANEL}
   * @param tonoSugerido el color que la lectura de fotos vio en ella, si mostraba uno solo: lo que
   *     el panel propone al aprobar; nulo si no hay sugerencia
   * @param tonosVistos los colores que la lectura vio en ella si muestra varios, como los nombró:
   *     los que el panel propone como prendas sin foto propia. Vacía si muestra uno o ninguno
   * @param tallas las del rótulo impreso en ella, como se venden en este borrador: las que el panel
   *     propone a su prenda (ADR-0085). Vacía si no trae
   */
  public record FotoRespuesta(
      UUID mensajeId,
      String url,
      String pieDeFoto,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"PROVEEDOR", "PANEL"})
          String origen,
      String tonoSugerido,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tonosVistos,
      List<String> tallas) {

    public static FotoRespuesta de(VerBorrador.FotoDeBorrador foto) {
      return new FotoRespuesta(
          foto.mensajeId(),
          foto.url(),
          foto.pieDeFoto(),
          foto.origen().name(),
          foto.tonoSugerido(),
          foto.tonosVistos(),
          foto.tallas());
    }
  }
}
