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
    BorradorRespuesta borrador, List<String> textos, List<FotoRespuesta> fotos) {

  public static BorradorDetalleRespuesta de(VerBorrador.DetalleDeBorrador detalle) {
    return new BorradorDetalleRespuesta(
        BorradorRespuesta.de(detalle.borrador()),
        detalle.textos(),
        detalle.fotos().stream().map(FotoRespuesta::de).toList());
  }

  /**
   * @param mensajeId el id de la foto, venga del proveedor o del panel: es el que se manda al
   *     aprobar y al quitarla
   * @param url nula cuando la exportación omitió el archivo: la foto existió y no está
   * @param origen {@code PROVEEDOR} o {@code PANEL}
   * @param tonoSugerido el color que la lectura de fotos vio en ella, si mostraba uno solo: lo que
   *     el panel propone al aprobar; nulo si no hay sugerencia
   */
  public record FotoRespuesta(
      UUID mensajeId,
      String url,
      String pieDeFoto,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"PROVEEDOR", "PANEL"})
          String origen,
      String tonoSugerido) {

    public static FotoRespuesta de(VerBorrador.FotoDeBorrador foto) {
      return new FotoRespuesta(
          foto.mensajeId(),
          foto.url(),
          foto.pieDeFoto(),
          foto.origen().name(),
          foto.tonoSugerido());
    }
  }
}
