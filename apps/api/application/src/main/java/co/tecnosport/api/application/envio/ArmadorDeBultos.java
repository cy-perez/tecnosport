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
 *
 * <p><strong>Con un piso y un techo</strong>, y los dos extremos del mismo rango se tratan distinto
 * a propósito:
 *
 * <ul>
 *   <li><strong>El piso se eleva</strong> ({@code adr/0035}). La plataforma exige un mínimo
 *       asegurable por bulto y rechaza la cotización <em>entera</em> si uno solo queda por debajo,
 *       así que un cable de 8.000 dentro de un pedido de 400.000 dejaba al comprador sin envío a
 *       domicilio y sin un error que lo explicara. Elevarlo no le quita nada a nadie.
 *   <li><strong>El techo rechaza</strong> ({@code adr/0036}). Recortar el declarado de un celular
 *       de 8.000.000 al tope de 5.000.000 haría que la transportadora responda hasta ahí si se
 *       pierde, y los tres millones restantes los pondría el negocio. Eso no es un ajuste de borde,
 *       así que ese artículo no va a domicilio y el checkout lo dice.
 * </ul>
 *
 * <p>Los dos límites llegan de fuera, en pesos y sin nombre de proveedor: quién los exige es
 * problema de {@code bootstrap}.
 */
public final class ArmadorDeBultos {

  private final RepositorioProductos repositorioProductos;
  private final Dinero valorDeclaradoMinimo;
  private final Dinero valorDeclaradoMaximo;

  public ArmadorDeBultos(
      RepositorioProductos repositorioProductos,
      Dinero valorDeclaradoMinimo,
      Dinero valorDeclaradoMaximo) {
    this.repositorioProductos =
        Objects.requireNonNull(
            repositorioProductos, "El repositorio de productos no puede ser nulo.");
    this.valorDeclaradoMinimo =
        Objects.requireNonNull(
            valorDeclaradoMinimo, "El valor declarado mínimo no puede ser nulo.");
    this.valorDeclaradoMaximo =
        Objects.requireNonNull(
            valorDeclaradoMaximo, "El valor declarado máximo no puede ser nulo.");
  }

  public List<BultoDespachable> armar(List<LineaAEmpacar> lineas) {
    Objects.requireNonNull(lineas, "Las líneas a empacar no pueden ser nulas.");
    List<BultoDespachable> bultos = new ArrayList<>();
    // Se recogen todos los que se pasan del techo y se falla al final, no en el primero: quitar un
    // artículo del carrito y volver a chocar con el siguiente es cómo se abandona un carrito.
    List<ArticuloNoAsegurableException.Articulo> noAsegurables = new ArrayList<>();
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
      Dinero declarado = Objects.requireNonNullElseGet(linea.valorDeclarado(), variante::precio);
      if (superaElMaximo(declarado)) {
        noAsegurables.add(
            new ArticuloNoAsegurableException.Articulo(variante.id(), producto.nombre()));
        continue;
      }
      Dinero valorDeclarado = alMenosElMinimo(declarado);
      for (int unidad = 0; unidad < linea.cantidad(); unidad++) {
        bultos.add(new BultoDespachable(new Bulto(variante.paquete(), valorDeclarado), contenido));
      }
    }
    if (!noAsegurables.isEmpty()) {
      throw new ArticuloNoAsegurableException(noAsegurables);
    }
    return List.copyOf(bultos);
  }

  /**
   * El piso se aplica <strong>por bulto y no por pedido</strong>, porque así es como lo valida la
   * plataforma. Con un bulto por unidad eso significa que tres artículos baratos declaran tres
   * veces el mínimo, y el total declarado puede superar lo facturado: no nos da nada —la
   * reclamación se paga contra la factura— y es el precio de cumplirlo ({@code adr/0035}).
   */
  private Dinero alMenosElMinimo(Dinero valorDeclarado) {
    return valorDeclarado.valor().compareTo(valorDeclaradoMinimo.valor()) < 0
        ? valorDeclaradoMinimo
        : valorDeclarado;
  }

  /**
   * El techo se mira contra el valor <strong>de una unidad</strong>, que es lo que va en un bulto.
   * Dos celulares de tres millones caben —son dos bultos de tres, y la plataforma valida por
   * bulto—; uno de seis no, y no hay forma de partirlo (docs/13 §6.13).
   */
  private boolean superaElMaximo(Dinero valorDeclarado) {
    return valorDeclarado.valor().compareTo(valorDeclaradoMaximo.valor()) > 0;
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
