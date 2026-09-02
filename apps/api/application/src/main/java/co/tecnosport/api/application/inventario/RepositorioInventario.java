package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.inventario.Inventario;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de inventario. Sin caso de uso propio todavía: reservar ocurre al iniciar el pago
 * (docs/00-producto.md), y el checkout es Fase 3. Este puerto ya queda listo para ese momento —
 * {@code infrastructure} lo implementa y las pruebas de concurrencia ("dos compradores por la
 * última unidad", docs/09-plan-de-arranque.md) lo ejercitan directo contra Postgres real, igual que
 * el dominio del catálogo no tuvo controlador hasta la capa de presentación en Fase 1.
 *
 * <p>La implementación de producción toma bloqueo pesimista sobre la fila de la variante al {@code
 * buscarPorVarianteId}, para que dos compradores simultáneos no reserven la misma última unidad.
 */
public interface RepositorioInventario {

  Optional<Inventario> buscarPorVarianteId(UUID varianteId);

  void guardar(Inventario inventario);
}
