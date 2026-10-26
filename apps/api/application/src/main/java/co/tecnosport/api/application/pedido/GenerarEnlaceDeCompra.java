package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El proveedor confirmó que tiene el color: se genera el enlace con que esa persona lo compra, y se
 * le aparta la unidad mientras el enlace esté vigente (ADR-0086).
 *
 * <p>Apartar es una reserva de inventario que vence con el enlace. Si el libro no tiene unidad
 * libre —la lista no marcó ese color, o la última se vendió—, entra una: el proveedor acaba de
 * decir que la tiene, y eso es lo que la existencia de tecnología cuenta (ADR-0075).
 *
 * <p>Quien llama abre la transacción.
 */
public final class GenerarEnlaceDeCompra {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioEnlacesDeCompra repositorioEnlaces;
  private final Reloj reloj;
  private final Duration vigencia;

  public GenerarEnlaceDeCompra(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      RepositorioEnlacesDeCompra repositorioEnlaces,
      Reloj reloj,
      Duration vigencia) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.repositorioEnlaces = Objects.requireNonNull(repositorioEnlaces);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigencia = Objects.requireNonNull(vigencia, "El enlace de compra tiene vigencia.");
    if (vigencia.isNegative() || vigencia.isZero()) {
      throw new IllegalArgumentException("La vigencia del enlace de compra es positiva.");
    }
  }

  public Resultado ejecutar(UUID varianteId) {
    Objects.requireNonNull(varianteId, "El enlace es de una variante.");
    // Las mismas comprobaciones que hará el pedido: un enlace que no puede terminar en compra solo
    // le hace perder el tiempo a quien lo recibe.
    Producto producto = ProductoVendible.exigir(repositorioProductos, varianteId);
    Variante variante =
        producto.variantes().stream()
            .filter(v -> v.id().equals(varianteId))
            .filter(Variante::seVende)
            .findFirst()
            .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
    if (!variante.disponibilidadPorConfirmar()) {
      throw new ExcepcionDeDominio(
          producto.nombre()
              + " no se confirma con el proveedor antes de venderse: se compra sin enlace.");
    }

    Instant ahora = reloj.ahora();
    Inventario libro = repositorioInventario.abrirLibroConBloqueo(varianteId);
    boolean entradaPropia = libro.saldoDisponible(ahora) < 1;
    if (entradaPropia) {
      libro.registrarEntrada(
          1, "El proveedor confirmó la disponibilidad para un enlace de compra", ahora);
    }
    MovimientoInventario reserva = libro.reservar(1, vigencia, ahora);
    repositorioInventario.guardar(libro);

    EnlaceDeCompra enlace =
        EnlaceDeCompra.generar(varianteId, reserva.id(), vigencia, ahora, entradaPropia);
    repositorioEnlaces.guardar(enlace);
    return new Resultado(enlace, producto, variante);
  }

  /** El enlace, con lo que el panel necesita para armar la URL y el mensaje. */
  public record Resultado(EnlaceDeCompra enlace, Producto producto, Variante variante) {}
}
