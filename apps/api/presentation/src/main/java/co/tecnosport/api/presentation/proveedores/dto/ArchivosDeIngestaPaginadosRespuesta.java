package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.ArchivosDeIngestaPaginados;
import java.util.List;

public record ArchivosDeIngestaPaginadosRespuesta(
    List<ArchivoDeIngestaRespuesta> items, int pagina, int totalPaginas, long totalArchivos) {

  public static ArchivosDeIngestaPaginadosRespuesta de(ArchivosDeIngestaPaginados pagina) {
    return new ArchivosDeIngestaPaginadosRespuesta(
        pagina.items().stream().map(ArchivoDeIngestaRespuesta::de).toList(),
        pagina.pagina(),
        pagina.totalPaginas(),
        pagina.totalArchivos());
  }
}
