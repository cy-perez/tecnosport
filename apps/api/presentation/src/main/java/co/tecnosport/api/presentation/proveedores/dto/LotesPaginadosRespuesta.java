package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.LotesPaginados;
import java.util.List;

public record LotesPaginadosRespuesta(
    List<LoteIngestaRespuesta> items, int pagina, int totalPaginas, long totalLotes) {

  public static LotesPaginadosRespuesta de(LotesPaginados pagina) {
    return new LotesPaginadosRespuesta(
        pagina.items().stream().map(LoteIngestaRespuesta::de).toList(),
        pagina.pagina(),
        pagina.totalPaginas(),
        pagina.totalLotes());
  }
}
