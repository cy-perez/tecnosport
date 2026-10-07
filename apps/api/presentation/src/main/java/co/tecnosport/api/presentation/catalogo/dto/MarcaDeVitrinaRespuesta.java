package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Una marca del filtro de la vitrina, con las líneas en las que tiene algo publicado.
 *
 * <p><b>DTO aparte y no un campo más en {@link MarcaRespuesta}</b>, que es lo contrario de lo que
 * decidió {@code CategoriaRespuesta} con sus hashtags. La cuenta sale distinta: allí forkear el DTO
 * costaba tocar tres endpoints del panel para ahorrar dos kilobytes de dato publicable; aquí el que
 * se quedaría con el campo de más es {@code /api/v1/admin/marcas}, que lista <b>todas</b> las
 * marcas —incluida la que no tiene ni un producto, que es justo para lo que existe— y tendría que
 * devolver una lista de líneas vacía en cada una. Un campo que en la mitad de los usos solo puede
 * mentir no es un campo compartido.
 *
 * <p>Las líneas son los nombres del enum {@code LineaCatalogo}, igual que {@code
 * CategoriaRespuesta.linea}: el cliente ya compara contra esos valores para acotar las categorías.
 */
public record MarcaDeVitrinaRespuesta(UUID id, String nombre, List<String> lineas) {}
