package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Producto;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Oculta lo que el proveedor lleva demasiado sin anunciar.
 *
 * <p>Un producto de proveedor que no ha aparecido en ningún mensaje en más de la ventana pasa a
 * {@code OCULTO_POR_VENCIMIENTO}: deja de listarse y de comprarse, su ficha sigue respondiendo, y
 * el siguiente mensaje que lo traiga lo reactiva. <b>Se oculta, no se borra</b>, y <b>nunca toca un
 * manual</b>: la consulta solo devuelve productos de origen proveedor y el agregado lo vuelve a
 * exigir. Idempotente: correrlo dos veces seguidas oculta una vez.
 *
 * <p>El límite se calcula con el reloj del puerto, así que la prueba mueve el reloj y no espera.
 */
public final class ExpirarDisponibilidadDeProductos {

  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final Reloj reloj;
  private final Duration ventana;

  public ExpirarDisponibilidadDeProductos(
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos repositorioProductos,
      Reloj reloj,
      Duration ventana) {
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.reloj = Objects.requireNonNull(reloj);
    Objects.requireNonNull(ventana, "La ventana de disponibilidad no puede ser nula.");
    if (ventana.isNegative() || ventana.isZero()) {
      throw new IllegalArgumentException("La ventana de disponibilidad tiene que ser positiva.");
    }
    this.ventana = ventana;
  }

  public Resultado ejecutar() {
    Instant limite = reloj.ahora().minus(ventana);
    List<Producto> vencidos = productosDeProveedor.disponiblesVistosAntesDe(limite);
    Map<UUID, Integer> porProveedor = new LinkedHashMap<>();
    for (Producto producto : vencidos) {
      producto.ocultarPorVencimiento();
      repositorioProductos.actualizar(producto);
      porProveedor.merge(producto.proveedorId().orElseThrow(), 1, Integer::sum);
    }
    return new Resultado(vencidos.size(), porProveedor, limite);
  }

  /** Cuántos se ocultaron y de quién, para el registro. */
  public record Resultado(int ocultados, Map<UUID, Integer> porProveedor, Instant limite) {
    public Resultado {
      porProveedor = Map.copyOf(porProveedor);
    }
  }
}
