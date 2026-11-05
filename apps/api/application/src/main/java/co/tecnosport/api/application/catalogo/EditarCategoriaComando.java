package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.List;
import java.util.UUID;

/**
 * Renombrar y mover son el mismo comando a propósito: el panel edita la categoría en un formulario,
 * y partirlo en dos casos de uso obligaría a dos peticiones para lo que quien administra vive como
 * un solo cambio —"esto se llama Bodis y va bajo Dama"—, con el estado intermedio raro de una
 * categoría ya renombrada pero todavía colgada donde estaba.
 *
 * <p>Las mismas reglas que en el alta: {@code padreId} vacío la deja colgando de {@code linea}, y
 * {@code padreId} presente hereda la línea del padre.
 *
 * <p><b>Los hashtags viajan aquí por la misma razón que el padre</b>, aunque en el dominio tengan
 * su propio camino: quien administra los escribe en el mismo formulario y los guarda de una vez.
 * Partirlos en otra petición habría dejado el estado intermedio de una categoría ya renombrada y
 * con las etiquetas de antes.
 *
 * <p>{@code hashtags} en nulo significa "no los toques" y la lista vacía significa "bórralos
 * todos". Son dos intenciones distintas y un solo campo no podría expresarlas si el nulo se tratara
 * como vacío: quien solo mueve una categoría de sitio no manda el campo, y no tiene por qué perder
 * las etiquetas por eso.
 *
 * <p>{@code emoji} sigue la misma regla con su forma propia: nulo es "no lo toques" y la cadena
 * vacía —o en blanco— es "quítalo", que deja la categoría usando el de su rama. No hace falta un
 * tercer valor porque {@link co.tecnosport.api.domain.catalogo.Categoria} ya lee el vacío como
 * nulo: borrar el campo en el panel es volver al heredado.
 */
public record EditarCategoriaComando(
    UUID id,
    String nombre,
    String slug,
    LineaCatalogo linea,
    UUID padreId,
    List<String> hashtags,
    String emoji) {

  /** El comando de toda la vida, para quien edita la categoría sin tocar las etiquetas. */
  public EditarCategoriaComando(
      UUID id, String nombre, String slug, LineaCatalogo linea, UUID padreId) {
    this(id, nombre, slug, linea, padreId, null, null);
  }

  /** Con etiquetas y sin tocar el emoji. */
  public EditarCategoriaComando(
      UUID id,
      String nombre,
      String slug,
      LineaCatalogo linea,
      UUID padreId,
      List<String> hashtags) {
    this(id, nombre, slug, linea, padreId, hashtags, null);
  }
}
