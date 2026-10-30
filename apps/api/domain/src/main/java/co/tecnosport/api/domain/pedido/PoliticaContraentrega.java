package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.Set;

/**
 * Las reglas de disponibilidad de contraentrega (docs/11-pagos-y-envios.md): cobertura de la ciudad
 * de destino, monto dentro del rango que recauda la transportadora, categorías excluidas, historial
 * de rechazos del comprador. Pura a propósito: quien orquesta ({@code application}) resuelve
 * cobertura y rechazo previo contra sus puertos antes de preguntarle a esta política — el dominio
 * no sabe de repositorios.
 */
public final class PoliticaContraentrega {

  private PoliticaContraentrega() {}

  /**
   * Si un producto <b>podría</b> pagarse contra entrega, antes de saber a dónde va: lo que se
   * pregunta al anunciarlo, cuando no hay ciudad, comprador ni flete.
   *
   * <p>Es la parte de {@link #disponible} que depende del producto —que esté habilitada, que la
   * línea no esté excluida y que la variante más cara no pase del techo— y vive aquí para que no
   * haya dos reglas que deriven. Lo que no puede saber lo deja para quien lo anuncia: la cobertura
   * de la ciudad, y que el flete puede empujar el total por encima del techo. El piso no se mira:
   * el flete suma, así que un producto barato puede llegar a él.
   */
  public static boolean podriaAdmitirse(
      CriteriosContraentrega criterios, Dinero precioMasAlto, LineaCatalogo linea) {
    Objects.requireNonNull(criterios, "Los criterios de contraentrega no pueden ser nulos.");
    Objects.requireNonNull(precioMasAlto, "El precio no puede ser nulo.");
    Objects.requireNonNull(linea, "La línea de catálogo no puede ser nula.");
    return criterios.habilitada()
        && !criterios.categoriasExcluidas().contains(linea)
        && precioMasAlto.valor().compareTo(criterios.montoMaximo().valor()) <= 0;
  }

  public static boolean disponible(
      CriteriosContraentrega criterios,
      Dinero total,
      Set<LineaCatalogo> categoriasEnElCarrito,
      boolean ciudadCubierta,
      boolean compradorConRechazoPrevio) {
    Objects.requireNonNull(criterios, "Los criterios de contraentrega no pueden ser nulos.");
    Objects.requireNonNull(total, "El total no puede ser nulo.");
    Objects.requireNonNull(
        categoriasEnElCarrito, "Las categorías del carrito no pueden ser nulas.");
    if (!criterios.habilitada()) {
      return false;
    }
    if (!ciudadCubierta) {
      return false;
    }
    if (compradorConRechazoPrevio) {
      return false;
    }
    // El rango es de la transportadora, no del negocio: por debajo del piso no recauda, y por
    // encima del techo no asume el riesgo. Se compara contra el total —mercancía más flete—,
    // porque es lo que cobra en la puerta.
    if (total.valor().compareTo(criterios.montoMinimo().valor()) < 0) {
      return false;
    }
    if (total.valor().compareTo(criterios.montoMaximo().valor()) > 0) {
      return false;
    }
    return categoriasEnElCarrito.stream().noneMatch(criterios.categoriasExcluidas()::contains);
  }
}
