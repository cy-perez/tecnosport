package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Renombrar y/o mover una categoría. El id va en la ruta, no en el cuerpo: es el recurso.
 *
 * <p>{@code slug} vacío significa "déjalo como está" y no "derívalo otra vez del nombre". Es la
 * diferencia entre corregir una tilde y romper todas las URLs que apuntaban a esa categoría — lo
 * razona {@code EditarCategoria}.
 */
public record EditarCategoriaPeticion(String nombre, String slug, String linea, UUID padreId) {}
