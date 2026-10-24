package co.tecnosport.api.presentation.proveedores.dto;

/**
 * La ficha editada a mano de un borrador de tecnología.
 *
 * @param descripcion el texto que se publicará al aprobar; nulo o en blanco vuelve a la ficha que
 *     preparó la skill
 */
public record EditarFichaBorradorTecnologiaPeticion(String descripcion) {}
