import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_RETRACTOS } from '../domain/repositorio-retractos.puerto';
import { SolicitudRetracto } from '../domain/retracto.model';

/** Clave por pedido, compartida con las mutaciones para que invaliden solo la fila que cambió. */
export function claveRetractosDePedido(pedidoId: string) {
  return ['admin', 'retractos', pedidoId] as const;
}

/**
 * `enabled` colgado de que haya pedido: el componente vive dentro de una fila expandible, y sin
 * esto pediría los retractos de todos los pedidos de la página aunque nadie los haya abierto.
 */
export function usarRetractosDePedido(pedidoId: () => string | null) {
  const repositorio = inject(REPOSITORIO_RETRACTOS);

  return injectQuery(() => ({
    queryKey: claveRetractosDePedido(pedidoId() ?? ''),
    queryFn: (): Promise<readonly SolicitudRetracto[]> =>
      repositorio.listarDePedido(pedidoId() ?? ''),
    enabled: !!pedidoId(),
    staleTime: 15_000,
  }));
}
