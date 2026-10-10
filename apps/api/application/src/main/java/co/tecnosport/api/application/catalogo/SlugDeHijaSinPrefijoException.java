package co.tecnosport.api.application.catalogo;

/**
 * El slug de una subcategoría empieza por el de su rama. Cuando no se escribe, {@link
 * CrearCategoria} ya lo deriva así; esta excepción cubre el otro camino, el del campo <b>Slug</b>
 * del panel, que hasta el 10 de octubre de 2026 se tomaba tal cual sin mirar de quién cuelga.
 *
 * <p><b>Por qué es un rechazo y no una conveniencia.</b> El slug es único en toda la tabla y el
 * filtro de la vitrina viaja por él ({@code ?categoria=ropa-dama-busos}), así que un nombre que se
 * repite entre ramas —"Busos" y "Sudaderas" están en Dama y en Caballero, "Dama" en tres líneas—
 * solo cabe dos veces si el slug dice de qué rama es. Crear "Morrales" bajo {@code
 * bolsos-caballero} con el slug {@code morrales} funcionaba, y el daño llegaba después: el día que
 * Dama pidiera su "Morrales", choca contra el índice único y el mensaje habla de un slug repetido
 * sin decir que el problema real es el primero, escrito sin su rama hace meses.
 *
 * <p>Y no es teórico: {@code bolsos}, {@code ropa-deportiva} y {@code calzado-deportivo} fueron
 * exactamente eso —categorías planas que {@code V63} tuvo que borrar— y el 10 de octubre de 2026
 * hubo que barrer sus restos de 37 archivos de prueba. El panel podía volver a crearlos.
 *
 * <p>Vive en {@code application} y no en el dominio porque la regla necesita ver <b>otra</b> fila,
 * la del padre, y un agregado que consulta al repositorio deja de serlo. Mismo motivo que {@link
 * CategoriaSlugYaExisteException}.
 *
 * <p><b>Lo que esta regla no cubre, a propósito:</b> mover una categoría ya creada no recalcula su
 * slug —está en URLs compartidas e indexadas—, así que "Faldas" movida a Caballero conserva {@code
 * ropa-dama-faldas}. Eso se vigila escribiendo el slug nuevo a mano, que es cuando esta excepción
 * vuelve a aplicar.
 */
public final class SlugDeHijaSinPrefijoException extends RuntimeException {

  public SlugDeHijaSinPrefijoException(String slug, String slugDelPadre) {
    super(
        "El slug '"
            + slug
            + "' no empieza por el de su categoría madre: tiene que ser '"
            + slugDelPadre
            + "-' seguido del nombre. Déjalo vacío y se deriva solo.");
  }
}
