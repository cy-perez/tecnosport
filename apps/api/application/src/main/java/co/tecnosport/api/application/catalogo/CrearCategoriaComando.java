package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.UUID;

/**
 * Alta de una categoría. Dos de los tres campos opcionales se excluyen entre sí y eso es el modelo,
 * no un descuido:
 *
 * <ul>
 *   <li>{@code padreId} vacío significa "cuelga de la línea", y entonces {@code linea} es
 *       obligatoria.
 *   <li>{@code padreId} presente significa "cuelga de esa categoría", y entonces {@code linea}
 *       sobra: se hereda del padre. Si llega, se ignora.
 *   <li>{@code slug} vacío significa "derívalo del nombre", que es lo que hace el panel salvo que
 *       alguien quiera uno distinto.
 * </ul>
 */
public record CrearCategoriaComando(
    String nombre, String slug, LineaCatalogo linea, UUID padreId) {}
