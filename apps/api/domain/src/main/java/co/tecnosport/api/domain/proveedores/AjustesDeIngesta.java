package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Optional;

/**
 * Cómo se ingiere el chat de un proveedor cuando no se parece a los demás (ADR-0089, 25 de octubre
 * de 2026). Los cuatro nacieron con Tenis Al Pie, que manda tandas de 25 a 35 modelos distintos con
 * un solo precio, sin SKU, sin tallas en el anuncio y sin existencias.
 *
 * @param unaFotoUnProducto cada foto de la publicación es un producto distinto, no un modelo en
 *     varios colores: sin SKU, la regla de siempre dejaba la tanda entera en un borrador
 * @param ventaBajoConfirmacion lo que se aprueba de él se vende como la tecnología: la talla se
 *     confirma por WhatsApp antes de cobrar, con un enlace de compra (ADR-0086)
 * @param diasDeVigencia cuántos días sigue a la venta un producto que no vuelve a aparecer en el
 *     chat; vacío es la ventana general (ADR-0066)
 * @param tallasPorGenero las tallas que maneja siempre, para el calzado; vacío si las dice cada
 *     anuncio
 */
public record AjustesDeIngesta(
    boolean unaFotoUnProducto,
    boolean ventaBajoConfirmacion,
    Integer diasDeVigencia,
    TallasPorGenero tallasPorGenero) {

  /** Más de un mes sin aparecer en el chat no es vigencia: es un catálogo que ya no existe. */
  static final int MAXIMO_DE_DIAS = 30;

  /** Lo de todos los proveedores antes de Tenis Al Pie. */
  public static final AjustesDeIngesta POR_OMISION = new AjustesDeIngesta(false, false, null, null);

  public AjustesDeIngesta {
    if (diasDeVigencia != null && (diasDeVigencia < 1 || diasDeVigencia > MAXIMO_DE_DIAS)) {
      throw new ExcepcionDeDominio(
          "Los días de vigencia van de 1 a " + MAXIMO_DE_DIAS + ": " + diasDeVigencia + " no.");
    }
  }

  public Optional<Integer> diasDeVigenciaOpcional() {
    return Optional.ofNullable(diasDeVigencia);
  }

  public Optional<TallasPorGenero> tallasPorGeneroOpcional() {
    return Optional.ofNullable(tallasPorGenero);
  }

  public boolean porOmision() {
    return equals(POR_OMISION);
  }
}
