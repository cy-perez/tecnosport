package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarrito;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pone precio de hoy a cada línea del carrito.
 *
 * <p>Existe porque la pantalla de confirmar —la del artículo 50 de la Ley 1480, donde el comprador
 * acepta el total— sumaba precios que el navegador había guardado al agregar cada producto. Si el
 * precio cambiaba mientras tanto, la pantalla mostraba el viejo y Wompi cobraba el nuevo; si al
 * navegador le faltaba la foto de una línea, esa línea sumaba cero. El cobro siempre fue el del
 * servidor; lo que estaba mal era lo que se le informaba al comprador antes de pagar.
 *
 * <p>Mismo criterio de "vendible" que {@code CrearPedido}: producto publicado y variante activa.
 */
public final class CotizarCarrito {

  private final RepositorioCarrito repositorioCarrito;
  private final RepositorioProductos repositorioProductos;

  public CotizarCarrito(
      RepositorioCarrito repositorioCarrito, RepositorioProductos repositorioProductos) {
    this.repositorioCarrito = Objects.requireNonNull(repositorioCarrito);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public CarritoCotizado ejecutar(UUID carritoId) {
    Objects.requireNonNull(carritoId, "El carrito no puede ser nulo.");
    Carrito carrito =
        repositorioCarrito
            .buscarPorId(carritoId)
            .orElseThrow(() -> new CarritoNoEncontradoException(carritoId));
    List<CarritoCotizado.LineaCotizada> lineas = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal subtotalContado = BigDecimal.ZERO;
    for (LineaCarrito linea : carrito.lineas()) {
      Optional<Variante> variante = varianteVendible(linea.varianteId());
      Dinero precio = variante.map(Variante::precio).orElse(null);
      Dinero precioContado = variante.map(Variante::precioContado).orElse(null);
      Dinero subtotalLinea = porCantidad(precio, linea.cantidad());
      Dinero subtotalContadoLinea = porCantidad(precioContado, linea.cantidad());
      if (subtotalLinea != null) {
        subtotal = subtotal.add(subtotalLinea.valor());
        subtotalContado = subtotalContado.add(subtotalContadoLinea.valor());
      }
      lineas.add(
          new CarritoCotizado.LineaCotizada(
              linea.id(),
              linea.varianteId(),
              linea.cantidad(),
              precio,
              subtotalLinea,
              precioContado,
              subtotalContadoLinea));
    }
    return new CarritoCotizado(lineas, Dinero.deCop(subtotal), Dinero.deCop(subtotalContado));
  }

  private static Dinero porCantidad(Dinero precio, int cantidad) {
    return precio == null
        ? null
        : Dinero.deCop(precio.valor().multiply(BigDecimal.valueOf(cantidad)));
  }

  private Optional<Variante> varianteVendible(UUID varianteId) {
    return repositorioProductos
        .buscarPorVarianteId(varianteId)
        .filter(producto -> producto.estado() == EstadoProducto.PUBLICADO)
        .flatMap(producto -> variante(producto, varianteId))
        .filter(Variante::seVende);
  }

  private static Optional<Variante> variante(Producto producto, UUID varianteId) {
    return producto.variantes().stream().filter(v -> v.id().equals(varianteId)).findFirst();
  }
}
