package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.UUID;

/**
 * Renombrar y mover son el mismo comando a propósito: el panel edita la categoría en un formulario,
 * y partirlo en dos casos de uso obligaría a dos peticiones para lo que quien administra vive como
 * un solo cambio —"esto se llama Bodis y va bajo Dama"—, con el estado intermedio raro de una
 * categoría ya renombrada pero todavía colgada donde estaba.
 *
 * <p>Las mismas reglas que en el alta: {@code padreId} vacío la deja colgando de {@code linea}, y
 * {@code padreId} presente hereda la línea del padre.
 */
public record EditarCategoriaComando(
    UUID id, String nombre, String slug, LineaCatalogo linea, UUID padreId) {}
