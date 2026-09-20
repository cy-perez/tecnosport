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

  void guardar(Inventario inventario);
}
