package co.tecnosport.api.application.proveedores;

/**
 * Lo que dejó borrar un lote.
 *
 * @param productosEliminados los productos no publicados que salieron de él y se borraron
 * @param productosConservados los que se quedaron porque están publicados o ya se vendieron
 * @param archivosBorrados los objetos del bucket privado que se fueron con el lote
 */
public record LoteEliminado(
    int productosEliminados, int productosConservados, int archivosBorrados) {}
