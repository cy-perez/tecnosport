package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Retira un enlace de compra antes de que se use: la persona desistió o el proveedor ya no tiene la
 * unidad. La unidad apartada vuelve a quedar libre, y si entró al libro con el enlace, sale: nadie
 * más la confirmó. Quien llama abre la transacción.
 *
 * <p>Bloquea el enlace antes que el libro, en el mismo orden que {@code CrearPedido}. Leído sin
 * bloqueo, un pedido que lo usaba al mismo tiempo podía terminar primero y esta escritura le
 * borraba el pedido al enlace y lo dejaba anulado sobre una compra viva; y con el orden al revés,
 * los dos se esperaban el uno al otro.
 */
public final class AnularEnlaceDeCompra {

  private final RepositorioEnlacesDeCompra repositorioEnlaces;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public AnularEnlaceDeCompra(
      RepositorioEnlacesDeCompra repositorioEnlaces,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioEnlaces = Objects.requireNonNull(repositorioEnlaces);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public EnlaceDeCompra ejecutar(UUID enlaceId) {
    Objects.requireNonNull(enlaceId, "El enlace a anular tiene id.");
    EnlaceDeCompra enlace =
        repositorioEnlaces
            .buscarPorIdParaActualizar(enlaceId)
            .orElseThrow(() -> new EnlaceDeCompraNoEncontradoException(enlaceId));
    Instant ahora = reloj.ahora();
    // Primero el agregado: uno ya usado en un pedido no se anula, y entonces no se toca el libro.
    enlace.anular(ahora);
    Inventario libro = repositorioInventario.abrirLibroConBloqueo(enlace.varianteId());
    libro.liberar(enlace.reservaId(), "Enlace de compra anulado", ahora);
    if (enlace.entradaPropia() && libro.saldoDisponible(ahora) > 0) {
      libro.registrarAjuste(-1, "Sale la unidad que entró con el enlace de compra anulado", ahora);
    }
    repositorioInventario.guardar(libro);
    repositorioEnlaces.actualizar(enlace);
    return enlace;
  }
}
