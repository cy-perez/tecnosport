package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import java.util.List;

public record BorradoresPaginadosRespuesta(
    List<BorradorRespuesta> items, int pagina, int totalPaginas, long totalBorradores) {

  public static BorradoresPaginadosRespuesta de(
      BorradoresPaginados pagina, ComisionDePasarela comision) {
    return new BorradoresPaginadosRespuesta(
        pagina.items().stream().map(b -> BorradorRespuesta.de(b, comision)).toList(),
        pagina.pagina(),
        pagina.totalPaginas(),
        pagina.totalBorradores());
  }
}
