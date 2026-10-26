package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

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
 *
 * <h2>La tecnología tiene su propia ventana</h2>
 *
 * <p>Un proveedor de tecnología no anuncia en mensajes: manda una lista de precios cada varios
 * días, y lo que trae vale hasta la siguiente. Con la ventana de los mensajes, todo lo de una lista
 * se ocultaría a los tres días aunque el proveedor lo siguiera teniendo. Se busca con la ventana
 * más corta y lo de tecnología se perdona hasta que pase la suya.
 *
 * <h2>Y un proveedor puede tener la suya (25 de octubre de 2026)</h2>
 *
 * <p>Tenis Al Pie manda tandas cada dos o tres días: con la ventana de tres, un modelo que se
 * saltaba una tanda se ocultaba. El negocio le dio cinco ({@code AjustesDeIngesta#diasDeVigencia},
 * ADR-0089). Como una propia puede ser más corta que las dos generales, la búsqueda arranca en la
 * más corta posible —un día— y cada producto se mide con la de su proveedor.
 */
public final class ExpirarDisponibilidadDeProductos {

  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final Reloj reloj;
  private final RepositorioProveedores repositorioProveedores;
  private final Duration ventana;
  private final Duration ventanaTecnologia;

  public ExpirarDisponibilidadDeProductos(
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos repositorioProductos,
      RepositorioProveedores repositorioProveedores,
      Reloj reloj,
      Duration ventana,
      Duration ventanaTecnologia) {
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.reloj = Objects.requireNonNull(reloj);
    this.ventana = positiva(ventana);
    this.ventanaTecnologia = positiva(ventanaTecnologia);
  }

  private static Duration positiva(Duration ventana) {
    Objects.requireNonNull(ventana, "La ventana de disponibilidad no puede ser nula.");
    if (ventana.isNegative() || ventana.isZero()) {
      throw new IllegalArgumentException("La ventana de disponibilidad tiene que ser positiva.");
    }
    return ventana;
  }

  public Resultado ejecutar() {
    Instant ahora = reloj.ahora();
    Instant limite = ahora.minus(ventana);
    Instant limiteTecnologia = ahora.minus(ventanaTecnologia);
    Instant limiteDeBusqueda =
        Stream.of(limite, limiteTecnologia, ahora.minus(Duration.ofDays(1)))
            .max(Comparator.naturalOrder())
            .orElseThrow();
    Map<UUID, Instant> limiteDelProveedor = new HashMap<>();
    Map<UUID, Integer> porProveedor = new LinkedHashMap<>();
    int ocultados = 0;
    for (Producto producto : productosDeProveedor.disponiblesVistosAntesDe(limiteDeBusqueda)) {
      UUID proveedorId = producto.proveedorId().orElseThrow();
      Instant suLimite =
          limiteDelProveedor.computeIfAbsent(
              proveedorId,
              id ->
                  repositorioProveedores
                      .buscarPorId(id)
                      .map(p -> limiteDe(p, ahora, limite, limiteTecnologia))
                      .orElse(limite));
      if (!producto.vistoPorUltimaVez().orElseThrow().isBefore(suLimite)) {
        continue;
      }
      producto.ocultarPorVencimiento();
      repositorioProductos.actualizar(producto);
      porProveedor.merge(proveedorId, 1, Integer::sum);
      ocultados++;
    }
    return new Resultado(ocultados, porProveedor, limite);
  }

  /** El de su ventana propia si la tiene; si no, el de la tecnología o el general. */
  private static Instant limiteDe(
      Proveedor proveedor, Instant ahora, Instant limite, Instant limiteTecnologia) {
    return proveedor
        .ajustesDeIngesta()
        .diasDeVigenciaOpcional()
        .map(dias -> ahora.minus(Duration.ofDays(dias)))
        .orElse(proveedor.entraPorLista() ? limiteTecnologia : limite);
  }

  /** Cuántos se ocultaron y de quién, para el registro. */
  public record Resultado(int ocultados, Map<UUID, Integer> porProveedor, Instant limite) {
    public Resultado {
      porProveedor = Map.copyOf(porProveedor);
    }
  }
}
