package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.inventario.Inventario;
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

  void guardar(Inventario inventario);
}
