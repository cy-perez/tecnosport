package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Optional;

/**
 * Desde cuánto se anuncia cada crédito en el pie de una publicación ({@code ADR-0107}).
 *
 * <p>Vacío es «no se anuncia»: Sistecrédito mientras la pasarela esté apagada, y Addi mientras no
 * esté integrado al checkout. Los dos los rechaza su proveedor por debajo de un mínimo que define
 * él, así que anunciarlos en un producto que no lo alcanza es prometer algo que el crédito niega.
 *
 * @param sistecredito el mismo mínimo que aplica el checkout ({@code
 *     tecnosport.sistecredito.monto-minimo}), no una copia
 * @param addi el de {@code tecnosport.meta.addi-monto-minimo}, que solo existe para la difusión
 *     hasta que Addi llegue al checkout
 */
public record MinimosDeCredito(Optional<Dinero> sistecredito, Optional<Dinero> addi) {

  public MinimosDeCredito {
    sistecredito = sistecredito == null ? Optional.empty() : sistecredito;
    addi = addi == null ? Optional.empty() : addi;
  }

  /** Ningún crédito anunciado. */
  public static MinimosDeCredito ninguno() {
    return new MinimosDeCredito(Optional.empty(), Optional.empty());
  }

  boolean anunciaSistecredito(Dinero precio) {
    return sistecredito.filter(minimo -> alcanza(precio, minimo)).isPresent();
  }

  boolean anunciaAddi(Dinero precio) {
    return addi.filter(minimo -> alcanza(precio, minimo)).isPresent();
  }

  private static boolean alcanza(Dinero precio, Dinero minimo) {
    return precio.valor().compareTo(minimo.valor()) >= 0;
  }
}
