package co.tecnosport.api.application.catalogo;

/**
 * Una talla del filtro de la vitrina.
 *
 * @param valor la talla normalizada, la que viaja en el filtro: «UNICA», «XXL»
 * @param etiqueta como la escribe el catálogo, la que se lee: «Única»
 */
public record OpcionDeTalla(String valor, String etiqueta) {}
