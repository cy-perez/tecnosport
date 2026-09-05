import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { FiltroPedidosAdmin, PedidosPaginadosAdmin } from '../domain/pedido-admin.model';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../domain/repositorio-pedidos-admin.puerto';

/** Clave compartida con las mutaciones de acciones (`usar-acciones-pedido-admin.ts`): al
 * terminar una acción, invalidan exactamente esta clave para refrescar la fila sin recargar
 * la página (apps/web/CLAUDE.md: "las mutaciones invalidan por clave"). */
export function claveListaPedidosAdmin(filtro: FiltroPedidosAdmin) {
  return ['admin', 'pedidos', filtro] as const;
}

/** Paginado por página, no scroll infinito — un panel administrativo, no una vitrina
 * (apps/web/CLAUDE.md: "docs/03-api.md: cursor en el catálogo, página en el panel administrativo"). */
export function usarListarPedidosAdmin(filtro: () => FiltroPedidosAdmin) {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);

  return injectQuery(() => ({
    queryKey: claveListaPedidosAdmin(filtro()),
    queryFn: (): Promise<PedidosPaginadosAdmin> => repositorio.listar(filtro()),
    staleTime: 15_000,
  }));
}
