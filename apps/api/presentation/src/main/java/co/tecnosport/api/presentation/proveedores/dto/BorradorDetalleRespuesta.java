package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.VerBorrador;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * El borrador con lo que hace falta para revisarlo: los textos originales del proveedor y sus fotos
 * con URL firmada de lectura, que caduca en minutos porque el bucket es privado.
 */
public record BorradorDetalleRespuesta(
    BorradorRespuesta borrador,
    List<String> textos,
    List<FotoRespuesta> fotos,
    List<HermanoRespuesta> hermanos) {

  public static BorradorDetalleRespuesta de(VerBorrador.DetalleDeBorrador detalle) {
    return new BorradorDetalleRespuesta(
        BorradorRespuesta.de(detalle.borrador()),
        detalle.textos(),
        detalle.fotos().stream().map(FotoRespuesta::de).toList(),
        detalle.hermanos().stream()
            .map(h -> new HermanoRespuesta(h.id(), h.titulo(), h.mismoDiseno()))
            .toList());
  }

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
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tonosVistos) {

    public static FotoRespuesta de(VerBorrador.FotoDeBorrador foto) {
      return new FotoRespuesta(
          foto.mensajeId(),
          foto.url(),
          foto.pieDeFoto(),
          foto.origen().name(),
          foto.tonoSugerido(),
          foto.tonosVistos());
    }
  }
}
