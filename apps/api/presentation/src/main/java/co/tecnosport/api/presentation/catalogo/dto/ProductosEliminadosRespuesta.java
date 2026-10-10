package co.tecnosport.api.presentation.catalogo.dto;

import co.tecnosport.api.application.catalogo.ProductosEliminados;
import java.util.UUID;

/**
 * Lo que dejó una tanda del borrado en bloque. Con {@code siguiente} el panel pide la próxima tanda
 * pasándolo como {@code desde}, y {@code hasta} tal como vino; nulo, terminó.
 */
public record ProductosEliminadosRespuesta(
    int eliminados,
    int conservadosPorVentas,
    int conservadosPorExistencias,
    UUID siguiente,
    UUID hasta) {

  public static ProductosEliminadosRespuesta de(ProductosEliminados tanda) {
    return new ProductosEliminadosRespuesta(
        tanda.eliminados(),
        tanda.conservadosPorVentas(),
        tanda.conservadosPorExistencias(),
        tanda.siguiente(),
        tanda.hasta());
  }
}
