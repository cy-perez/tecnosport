package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Una categoría del árbol, plana. {@code padreId} viene nulo en las de primer nivel, que cuelgan
 * directamente de {@code linea}.
 *
 * <p>Se devuelve la lista plana y no un árbol anidado a propósito: el cliente que pinta el menú
 * necesita el árbol, pero el que pinta el desplegable del filtro necesita la lista, y anidar aquí
 * obliga al segundo a aplanar lo que el primero va a colgar. Con {@code padreId} cada uno arma lo
 * suyo en una pasada, y la respuesta sigue cabiendo en el {@code ResultadoPaginadoRespuesta} que ya
 * usan marcas y atributos.
 */
public record CategoriaRespuesta(UUID id, String nombre, String slug, String linea, UUID padreId) {}
