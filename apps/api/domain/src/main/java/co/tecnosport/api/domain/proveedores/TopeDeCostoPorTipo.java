package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Map;
import java.util.Optional;

/**
 * Lo más que se le paga a un proveedor por una prenda de un tipo: lo que cuesta más no se le
 * compra, y el anuncio se descarta sin dejar borrador.
 *
 * <p>Decidido por el negocio el 13 de octubre de 2026, para todos los proveedores: los jeans, los
 * pantalones, los joggers, las bermudas y los shorts que el proveedor anuncia por encima de 90.000
 * no se venden. Exactamente 90.000 todavía pasa. Lo trajeron los «JEANS Importado para Caballero»
 * de La Riverah a 125.000, que salían en doce borradores.
 *
 * <p>Se compara el costo de la prenda sola, nunca el de un set que la incluya: un pantalón con su
 * blusa cuesta más que el pantalón, y lo que el negocio fijó es el precio del pantalón. Tampoco
 * aplica a un agotado: el que ya se vendía tiene que poder agotarse aunque hoy costara más.
 */
public final class TopeDeCostoPorTipo {

  private static final Dinero NOVENTA_MIL = Dinero.deCop(90000);

  /** La sudadera es el pantalón deportivo, el jogger; la bermuda sale como short. */
  private static final Map<TipoProductoProveedor, Dinero> TOPES =
      Map.of(
          TipoProductoProveedor.PANTALON, NOVENTA_MIL,
          TipoProductoProveedor.SUDADERA, NOVENTA_MIL,
          TipoProductoProveedor.SHORT, NOVENTA_MIL);

  private TopeDeCostoPorTipo() {}

  public static Optional<Dinero> de(TipoProductoProveedor tipo) {
    return Optional.ofNullable(TOPES.get(tipo));
  }

  /** ¿Pasa el costo del tope de su tipo? Sin costo o sin tope, no. */
  public static boolean loExcede(TipoProductoProveedor tipo, Dinero costo) {
    return costo != null
        && de(tipo).map(tope -> costo.valor().compareTo(tope.valor()) > 0).orElse(false);
  }
}
