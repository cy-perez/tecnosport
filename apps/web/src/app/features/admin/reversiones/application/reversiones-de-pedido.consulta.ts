import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_REVERSIONES } from '../domain/repositorio-reversiones.puerto';
import { SolicitudReversion } from '../domain/reversion.model';

export function claveReversionesDePedido(pedidoId: string) {
  return ['admin', 'reversiones', pedidoId] as const;
}

/** `enabled` colgado del pedido, mismo motivo que el retracto: vive en una fila expandible. */
export function usarReversionesDePedido(pedidoId: () => string | null) {
  const repositorio = inject(REPOSITORIO_REVERSIONES);

  return injectQuery(() => ({
    queryKey: claveReversionesDePedido(pedidoId() ?? ''),
    queryFn: (): Promise<readonly SolicitudReversion[]> =>
      repositorio.listarDePedido(pedidoId() ?? ''),
    enabled: !!pedidoId(),
    staleTime: 15_000,
  }));
}
