import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PaquetesDePedido } from '../domain/paquetes-de-pedido.model';
import { REPOSITORIO_PAQUETES_PEDIDO } from '../domain/repositorio-paquetes-pedido.puerto';

/**
 * `staleTime` en cero: los paquetes salen de las referencias de envío de hoy, que se cambian en
 * otra pantalla, y lo que se va a copiar en la plataforma tiene que ser lo de este momento.
 * `enabled` colgado del pedido por lo mismo que los retractos: vive en una fila expandible.
 */
export function usarPaquetesDePedido(pedidoId: () => string | null) {
  const repositorio = inject(REPOSITORIO_PAQUETES_PEDIDO);

  return injectQuery(() => ({
    queryKey: ['admin', 'envios', 'paquetes', pedidoId() ?? ''] as const,
    queryFn: (): Promise<PaquetesDePedido> => repositorio.consultar(pedidoId() ?? ''),
    enabled: !!pedidoId(),
    staleTime: 0,
    // Un 409 —un artículo sin medidas, o uno que no se puede asegurar— no se arregla reintentando.
    retry: false,
  }));
}
