package co.tecnosport.api.application.catalogo;

/**
 * El árbol del catálogo llega hasta dos niveles bajo la línea —"Ropa › Dama › Camisas"— y ahí para.
 *
 * <p><b>Por qué hay un tope.</b> No es una limitación técnica: la columna {@code padre_id} aguanta
 * la profundidad que sea. Es que el menú del sitio pinta el árbol entero desplegable, y un tercer
 * nivel de sangría no cabe en el riel lateral ni en el panel del teléfono. Un árbol que el menú no
 * puede pintar es un árbol que el comprador no puede recorrer, y la categoría que quede ahí abajo
 * existe solo para quien administra.
 *
 * <p>Si algún día el negocio necesita un nivel más, el cambio es aquí y en cómo pinta el menú, en
 * ese orden. Lo que no puede pasar es que se cuele por descuido desde el panel.
 */
public final class ProfundidadDeCategoriaExcedidaException extends RuntimeException {

  public ProfundidadDeCategoriaExcedidaException(String nombrePadre) {
    super(
        "La categoría '"
            + nombrePadre
            + "' ya es una subcategoría: el árbol del catálogo llega hasta dos niveles bajo la"
            + " línea.");
  }
}
