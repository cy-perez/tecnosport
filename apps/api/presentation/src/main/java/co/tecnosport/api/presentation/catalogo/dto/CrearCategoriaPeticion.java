package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Alta de categoría desde el panel.
 *
 * <p>{@code slug} y {@code linea} son opcionales y se excluyen con {@code padreId}: sin padre la
 * categoría cuelga de la línea y entonces {@code linea} es obligatoria; con padre la hereda. El
 * slug vacío se deriva del nombre. Lo explica {@code CrearCategoriaComando}, que es quien manda.
 */
public record CrearCategoriaPeticion(String nombre, String slug, String linea, UUID padreId) {}
