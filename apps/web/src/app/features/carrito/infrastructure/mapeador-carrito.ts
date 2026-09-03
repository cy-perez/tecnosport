import type { components } from '@tecnosport/contratos';
import { Carrito, LineaCarrito } from '../domain/carrito.model';

type CarritoDto = components['schemas']['CarritoRespuesta'];
type LineaCarritoDto = components['schemas']['LineaCarritoRespuesta'];

/** DTO generado -> modelo propio del front. Ningún componente ve la forma de la respuesta HTTP. */
export function aCarrito(dto: CarritoDto): Carrito {
  return {
    id: dto.id ?? '',
    usuarioId: dto.usuarioId ?? null,
    lineas: (dto.lineas ?? []).map(aLineaCarrito),
    creadoEn: dto.creadoEn ?? '',
  };
}

function aLineaCarrito(dto: LineaCarritoDto): LineaCarrito {
  return {
    id: dto.id ?? '',
    varianteId: dto.varianteId ?? '',
    cantidad: dto.cantidad ?? 0,
  };
}
