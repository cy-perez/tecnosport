import type { components } from '@tecnosport/contratos';
import { Carrito, CarritoCotizado, LineaCarrito } from '../domain/carrito.model';

type CarritoDto = components['schemas']['CarritoRespuesta'];
type LineaCarritoDto = components['schemas']['LineaCarritoRespuesta'];
type CarritoCotizadoDto = components['schemas']['CarritoCotizadoRespuesta'];

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

/** Sin `?? 0` en el dinero: un precio que no vino es "no se vende", no un cero. */
export function aCarritoCotizado(dto: CarritoCotizadoDto): CarritoCotizado {
  return {
    subtotal: dto.subtotal.valor,
    lineas: dto.lineas.map((linea) => ({
      lineaId: linea.lineaId,
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
      precioUnitario: linea.precioUnitario?.valor ?? null,
      subtotal: linea.subtotal?.valor ?? null,
    })),
  };
}
