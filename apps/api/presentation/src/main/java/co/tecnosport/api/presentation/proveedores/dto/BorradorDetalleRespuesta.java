package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.VerBorrador;
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
        detalle.fotos().stream()
            .map(f -> new FotoRespuesta(f.mensajeId(), f.url(), f.pieDeFoto()))
            .toList());
  }

  /**
   * @param url nula cuando la exportación omitió el archivo: la foto existió y no está
   */
  public record FotoRespuesta(UUID mensajeId, String url, String pieDeFoto) {}
}
