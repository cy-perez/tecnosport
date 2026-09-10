import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ReclamacionGarantia } from '../domain/garantia.model';
import { REPOSITORIO_GARANTIAS } from '../domain/repositorio-garantias.puerto';

/** Clave por pedido, compartida con las mutaciones para que invaliden solo la fila que cambio. */
export function claveGarantiasDePedido(pedidoId: string) {
  return ['admin', 'garantias', pedidoId] as const;
}

/**
 * `enabled` colgado de que haya pedido, mismo motivo que en el retracto: el componente vive dentro
 * de una fila expandible y sin esto pediria las garantias de todos los pedidos de la pagina.
 */
export function usarGarantiasDePedido(pedidoId: () => string | null) {
  const repositorio = inject(REPOSITORIO_GARANTIAS);

  return injectQuery(() => ({
    queryKey: claveGarantiasDePedido(pedidoId() ?? ''),
    queryFn: (): Promise<readonly ReclamacionGarantia[]> =>
      repositorio.listarDePedido(pedidoId() ?? ''),
    enabled: !!pedidoId(),
    staleTime: 15_000,
  }));
}
