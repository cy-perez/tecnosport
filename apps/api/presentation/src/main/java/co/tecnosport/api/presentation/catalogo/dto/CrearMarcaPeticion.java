package co.tecnosport.api.presentation.catalogo.dto;

/**
 * Un solo campo, y sin {@code id}: el identificador lo genera el dominio ({@code Marca.crear}),
 * como en todo el catálogo.
 */
public record CrearMarcaPeticion(String nombre) {}
