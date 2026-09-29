package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Renombrar y/o mover una categoría. El id va en la ruta, no en el cuerpo: es el recurso.
 *
 * <p>{@code slug} vacío significa "déjalo como está" y no "derívalo otra vez del nombre". Es la
 * diferencia entre corregir una tilde y romper todas las URLs que apuntaban a esa categoría — lo
 * razona {@code EditarCategoria}.
 *
 * <p>{@code hashtags} sigue la misma idea y por eso <b>es opcional de verdad</b>: ausente significa
 * "no las toques" y la lista vacía significa "bórralas todas". Jackson 3 deja en nulo un componente
 * de tipo referencia que falte —no revienta, a diferencia de un primitivo—, así que el nulo llega
 * aquí tal cual y el caso de uso lo lee como la intención de no tocarlas.
 */
public record EditarCategoriaPeticion(
    String nombre, String slug, String linea, UUID padreId, List<String> hashtags) {}
