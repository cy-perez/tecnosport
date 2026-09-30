package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import java.util.List;

public record BorradoresPaginadosRespuesta(
    List<BorradorRespuesta> items, int pagina, int totalPaginas, long totalBorradores) {

  public static BorradoresPaginadosRespuesta de(BorradoresPaginados pagina) {
    return new BorradoresPaginadosRespuesta(
        pagina.items().stream().map(BorradorRespuesta::de).toList(),
        pagina.pagina(),
        pagina.totalPaginas(),
        pagina.totalBorradores());
  }
}
