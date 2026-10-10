package co.tecnosport.api.application.proveedores;

/**
 * Lo que dejó una tanda del borrado en bloque de borradores sin aprobar.
 *
 * @param eliminados los borradores que se borraron en esta tanda
 * @param archivosBorrados los objetos del bucket privado que se fueron con ellos
 * @param quedan los sin aprobar que siguen ahí después de la tanda: el panel pide otra mientras no
 *     sea cero
 */
public record BorradoresEliminados(int eliminados, int archivosBorrados, long quedan) {}
