package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.inventario.Inventario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de inventario. Consumido por {@code CrearPedido} y el aplicador de resultado de pago
 * (reservar/confirmar/liberar), y por {@code AgregarVariante} (Track B, Fase 4) para el alta
 * inicial de existencia de una variante nueva.
 *
 * <p>La implementación de producción toma bloqueo pesimista sobre la fila de la variante al {@code
 * buscarPorVarianteId}, para que dos compradores simultáneos no reserven la misma última unidad. Al
 * crear una variante nueva no hace falta ese bloqueo: no hay fila previa que dos escrituras
 * concurrentes puedan disputarse.
 */
public interface RepositorioInventario {

  Optional<Inventario> buscarPorVarianteId(UUID varianteId);

  /**
   * Todos los libros del catálogo, <b>sin bloqueo</b>, para que el panel pueda enseñar los saldos
   * (adr/0049). El bloqueo pesimista de {@link #buscarPorVarianteId} no sirve aquí y haría daño:
   * bloquearía el catálogo entero para pintar una pantalla de solo lectura.
   *
   * <p>Devuelve el agregado con sus movimientos y no un saldo ya calculado, porque el saldo —y
   * sobre todo qué reserva sigue vigente— lo sabe {@link Inventario} y nadie más. Es una lectura
   * cara a propósito: la alternativa era duplicar esa regla en SQL.
   */
  List<Inventario> listarTodos();

  /**
   * Los libros de un puñado de variantes, <b>sin bloqueo</b>, para que el catálogo público pueda
   * decir si algo se puede comprar (adr/0050). Es {@link #listarTodos} acotado a una página de la
   * vitrina: el mismo motivo para devolver el agregado y no un saldo ya calculado —quién sabe qué
   * reserva sigue vigente es {@link Inventario}— y el mismo precio, el histórico entero de cada
   * variante que se pide.
   *
   * <p>Las variantes sin libro <b>no salen en la lista</b>, no salen con saldo cero: el que llama
   * es el que decide qué significa esa ausencia. Para la vitrina significa agotado.
   */
  List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds);

  /**
   * El libro de una variante, abriéndolo si todavía no existe, y <b>siempre con el bloqueo</b> de
   * {@link #buscarPorVarianteId}.
   *
   * <p>Existe porque la alternativa no es segura. Quien necesitaba esto hacía {@code
   * buscarPorVarianteId(id).orElseGet(() -> Inventario.crear(id))}, y esa rama del {@code
   * orElseGet} no sostiene ningún bloqueo: el {@code select … for update} no encuentra fila, así
   * que no hay nada que bloquear y la transacción sigue creyendo que lo tiene. Dos conteos
   * simultáneos sobre una variante sin libro escribían dos agregados contra {@code
   * ux_inventario_variante}, y el que perdía moría con una violación de integridad sin traducir.
   *
   * <p>La implementación de producción inserta de forma idempotente y vuelve a leer con bloqueo, de
   * modo que las dos ramas acaban sosteniendo la misma garantía. Devuelve el libro, nunca un vacío:
   * si no existía, existe.
   */
  Inventario abrirLibroConBloqueo(UUID varianteId);

  void guardar(Inventario inventario);
}
