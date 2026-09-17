package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.ContenidoDeclarado;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cómo se empaca un pedido: un bulto por unidad, con su peso, sus medidas, su valor declarado y lo
 * que dirá su etiqueta.
 *
 * <p><strong>Un bulto por unidad y no por línea</strong> (decisión del 11 de septiembre de 2026):
 * tres camisetas son tres paquetes con el peso y las medidas reales de la variante. Sumar el peso
 * en un solo bulto obligaría a inventar las dimensiones de una caja combinada, y las dimensiones no
 * son un detalle porque las transportadoras cobran peso volumétrico.
 *
 * <p>Existe como pieza aparte porque <strong>lo usan dos</strong>: la cotización, que necesita los
 * bultos, y la emisión, que necesita además el contenido de cada uno y —esto es lo importante— en
 * el <em>mismo orden</em>. La plataforma empareja los paquetes del envío con los bultos de la
 * cotización por posición, así que si los dos caminos armaran su propia lista, el día que uno
 * cambiara el orden el contenido de una caja iría declarado en otra, y nada fallaría: la guía se
 * emite igual.
 *
 * <p>El peso y las medidas se leen del catálogo y no de la línea congelada del pedido, a propósito:
 * son del producto físico, no del precio, y si se corrigen porque estaban mal medidos el despacho
 * tiene que usar los buenos. <strong>El valor declarado es al revés</strong> y por eso entra por
 * {@link LineaAEmpacar}: es el monto que la transportadora paga si pierde el paquete, tiene que
 * coincidir con la factura, y esa dice lo que el comprador pagó — no lo que el producto cuesta hoy.
 */
public final class ArmadorDeBultos {

  private final RepositorioProductos repositorioProductos;

  public ArmadorDeBultos(RepositorioProductos repositorioProductos) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
  }

  public List<BultoDespachable> armar(List<LineaAEmpacar> lineas) {
    Objects.requireNonNull(lineas, "Las líneas a empacar no pueden ser nulas.");
    List<BultoDespachable> bultos = new ArrayList<>();
    for (LineaAEmpacar linea : lineas) {
      if (linea.cantidad() <= 0) {
        throw new IllegalArgumentException(
            "La cantidad de una línea debe ser mayor que cero: " + linea.cantidad());
      }
      Producto producto = producto(linea.varianteId());
      Variante variante = variante(producto, linea.varianteId());
      String contenido = ContenidoDeclarado.de(producto.categoria().linea());
      // El del pedido cuando lo hay —es el que el comprador pagó y contra el que se reclama—, y el
      // del catálogo cuando todavía no hay pedido, que es el caso del checkout.
      Dinero valorDeclarado =
          Objects.requireNonNullElseGet(linea.valorDeclarado(), variante::precio);
      for (int unidad = 0; unidad < linea.cantidad(); unidad++) {
        bultos.add(new BultoDespachable(new Bulto(variante.paquete(), valorDeclarado), contenido));
      }
    }
    return List.copyOf(bultos);
  }

  private Producto producto(UUID varianteId) {
    return repositorioProductos
        .buscarPorVarianteId(varianteId)
        .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
  }

  private static Variante variante(Producto producto, UUID varianteId) {
    return producto.variantes().stream()
        .filter(candidata -> candidata.id().equals(varianteId))
        .findFirst()
        .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
  }
}
