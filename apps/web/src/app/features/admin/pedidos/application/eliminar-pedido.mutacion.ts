import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../domain/repositorio-pedidos-admin.puerto';

/**
 * Eliminar un pedido. Aparte de `usarAccionesPedidoAdmin` porque la usa un componente de la fila
 * expandida, y aquella crea diez mutaciones que este no necesita. Invalida el mismo prefijo: la
 * fila desaparece de cualquier página o filtro que esté mirando la lista.
 */
export function usarEliminarPedido() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (pedidoId: string): Promise<void> => repositorio.eliminar(pedidoId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'pedidos'] }),
  }));
}
