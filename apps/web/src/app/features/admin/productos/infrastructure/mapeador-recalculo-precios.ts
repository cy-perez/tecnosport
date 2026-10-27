import type { components } from '@tecnosport/contratos';
import { PrecioPorRecalcular } from '../domain/producto-admin.model';

type PreciosPorRecalcularDto = components['schemas']['PreciosPorRecalcularRespuesta'];

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
