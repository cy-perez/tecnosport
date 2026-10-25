package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompraNoVigenteException;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que la ficha necesita saber de un enlace que llega en la URL: si sigue vigente y de qué
 * variante es. Sin sesión: el enlace es el permiso. Uno vencido, usado o anulado responde igual que
 * uno que no existe.
 */
public final class ConsultarEnlaceDeCompra {

  private final RepositorioEnlacesDeCompra repositorioEnlaces;
  private final Reloj reloj;

  public ConsultarEnlaceDeCompra(RepositorioEnlacesDeCompra repositorioEnlaces, Reloj reloj) {
    this.repositorioEnlaces = Objects.requireNonNull(repositorioEnlaces);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public EnlaceDeCompra ejecutar(String token) {
    if (token == null || token.isBlank()) {
      throw new EnlaceDeCompraNoVigenteException();
    }
    Instant ahora = reloj.ahora();
    return repositorioEnlaces
        .buscarPorToken(token.strip())
        .filter(e -> e.vigente(ahora))
        .orElseThrow(EnlaceDeCompraNoVigenteException::new);
  }
}
