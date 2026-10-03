package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
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
 *
 * <p><b>Los hashtags viajan también en la respuesta pública, y fue una decisión, no un
 * descuido.</b> Lo limpio habría sido partir el DTO como ya están partidos {@code
 * ProductoRespuesta} y {@code ProductoAdminRespuesta}, porque quien compra no necesita saber con
 * qué etiquetas se anuncia una categoría. Lo que lo desaconseja es la cuenta: forkear el DTO obliga
 * a tocar los tres endpoints del panel, el mapeador y el cliente de contratos, y lo que se ahorra
 * son unas ciento setenta cadenas cortas —dos kilobytes— en una llamada que el navegador cachea y
 * que además no es secreta: estas etiquetas existen precisamente para publicarse. Si algún día la
 * respuesta pública engorda por otras razones, este es el primer campo que debería salir.
 */
public record CategoriaRespuesta(
    UUID id,
    String nombre,
    String slug,
    String linea,
    UUID padreId,
    List<String> hashtags,
    /** Las tallas propias, en su orden; vacía si usa las de su rama o no talla. */
    List<String> escalaTallas) {}
