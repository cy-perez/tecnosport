package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.Set;

/**
 * Las cuatro reglas de disponibilidad de contraentrega (docs/11-pagos-y-envios.md): cobertura de la
 * ciudad de destino, monto máximo, categorías excluidas, historial de rechazos del comprador. Pura
 * a propósito: quien orquesta ({@code application}) resuelve cobertura y rechazo previo contra sus
 * puertos antes de preguntarle a esta política — el dominio no sabe de repositorios.
 */
public final class PoliticaContraentrega {

  private PoliticaContraentrega() {}

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
    if (total.valor().compareTo(criterios.montoMaximo().valor()) > 0) {
      return false;
    }
    return categoriasEnElCarrito.stream().noneMatch(criterios.categoriasExcluidas()::contains);
  }
}
