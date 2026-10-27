import type { components } from '@tecnosport/contratos';
import { PrecioPorRecalcular } from '../domain/producto-admin.model';

type PreciosPorRecalcularDto = components['schemas']['PreciosPorRecalcularRespuesta'];
type AplicarRecalculoDto = components['schemas']['AplicarRecalculoPeticion'];

/** DTO generado -> modelo del panel. Todos los campos son obligatorios en el contrato. */
export function aPreciosPorRecalcular(dto: PreciosPorRecalcularDto): PrecioPorRecalcular[] {
  return dto.variantes.map((variante) => ({
    varianteId: variante.varianteId,
    sku: variante.sku,
    nombreProducto: variante.nombreProducto,
    precioContado: variante.precioContado,
    publicadoActual: variante.publicadoActual,
    publicadoNuevo: variante.publicadoNuevo,
  }));
}

/**
 * Modelo -> petición: la fila tal como se vio, sin el SKU ni el nombre. El servidor la graba solo
 * si la variante sigue así.
 */
export function aPeticionDeRecalculo(
  revisadas: readonly PrecioPorRecalcular[],
): AplicarRecalculoDto {
  return {
    variantes: revisadas.map((fila) => ({
      varianteId: fila.varianteId,
      precioContado: fila.precioContado,
      publicadoActual: fila.publicadoActual,
      publicadoNuevo: fila.publicadoNuevo,
    })),
  };
}
