package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * Lo que dejó una tanda del borrado en bloque de productos no publicados.
 *
 * @param eliminados los que se borraron con sus fotos
 * @param conservadosPorVentas los que se quedaron porque alguna de sus variantes ya se vendió
 * @param conservadosPorExistencias los que se quedaron porque tienen unidades en el libro
 * @param objetosBorrados los objetos del bucket de imágenes que se fueron con ellos
 * @param siguiente el cursor de la tanda que sigue, o nulo si esta fue la última
 * @param hasta el tope fijado en la primera tanda, que las siguientes repiten; nulo si no había
 *     ningún producto en borrador
 */
public record ProductosEliminados(
    int eliminados,
    int conservadosPorVentas,
    int conservadosPorExistencias,
    int objetosBorrados,
    UUID siguiente,
    UUID hasta) {}
