package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.proveedores.MargenMinimo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La existencia de lo que se vende bajo confirmación (ADR-0089): el calzado de Tenis Al Pie se
 * vende como la tecnología. El proveedor no dice qué tallas tiene de cada modelo, así que la
 * existencia del catálogo no es la suya: es una cantidad nominal por talla para que el modelo se
 * vea a la venta, y la de verdad se confirma por WhatsApp antes de cobrar, con un enlace de compra
 * (ADR-0086).
 *
 * <p>Cada variante queda <b>por confirmar</b> al aprobarse, y su existencia libre sube a la nominal
 * al aprobarse y cada vez que el modelo vuelve a aparecer en el chat. Nunca baja: lo reservado y lo
 * vendido no se tocan, y si alguien contó más, se queda.
 *
 * <p>Lo usan la aprobación y la renovación del borrador, dentro de la transacción que ellas ya
 * tienen abierta: el libro se abre con bloqueo.
 */
public final class ExistenciaBajoConfirmacion {

  private static final String MOTIVO =
      "Venta bajo confirmación: existencia nominal, la talla se confirma por WhatsApp";

  private final RepositorioInventario repositorioInventario;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final Reloj reloj;
  private final int existenciaPorVariante;

  /**
   * @param existenciaPorVariante la nominal de cada talla; la misma que la tecnología por omisión
   */
  public ExistenciaBajoConfirmacion(
      RepositorioInventario repositorioInventario,
      RepositorioProductosDeProveedor productosDeProveedor,
      Reloj reloj,
      int existenciaPorVariante) {
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.reloj = Objects.requireNonNull(reloj);
    if (existenciaPorVariante < 1) {
      throw new IllegalArgumentException(
          "La existencia nominal de lo que se vende bajo confirmación es al menos 1.");
    }
    this.existenciaPorVariante = existenciaPorVariante;
  }

  /** Recién aprobado: todas sus variantes por confirmar, y con la existencia nominal. */
  public void alAprobar(Producto producto) {
    Map<UUID, Boolean> porConfirmar = new LinkedHashMap<>();
    producto.variantes().forEach(v -> porConfirmar.put(v.id(), true));
    productosDeProveedor.marcarDisponibilidadesPorConfirmar(porConfirmar);
    reponer(producto);
  }

  /**
   * El modelo volvió a aparecer en el chat: lo que se vende vuelve a la existencia nominal. Lo que
   * con el costo de hoy no deja el {@link MargenMinimo} no se repone (27 de octubre de 2026): la
   * renovación lo acaba de agotar, y el producto que llega aquí todavía no lo sabe.
   */
  public void reponer(Producto producto) {
    Instant ahora = reloj.ahora();
    for (Variante variante : producto.variantes()) {
      if (!variante.seVende()
          || !producto
              .precioProveedor()
              .map(costo -> MargenMinimo.alcanza(variante.precioContado(), costo))
              .orElse(true)) {
        continue;
      }
      Inventario libro = repositorioInventario.abrirLibroConBloqueo(variante.id());
      int faltan = existenciaPorVariante - libro.saldoDisponible(ahora);
      if (faltan > 0) {
        libro.registrarEntrada(faltan, MOTIVO, ahora);
        repositorioInventario.guardar(libro);
      }
    }
  }
}
