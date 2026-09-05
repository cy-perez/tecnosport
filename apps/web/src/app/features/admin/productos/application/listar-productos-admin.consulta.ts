import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { FiltroProductosAdmin, ProductosPaginadosAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function claveListaProductosAdmin(filtro: FiltroProductosAdmin) {
  return ['admin', 'productos', filtro] as const;
}

/** Paginado por página, no scroll infinito — un panel administrativo, no una vitrina
 * (apps/web/CLAUDE.md: "docs/03-api.md: cursor en el catálogo, página en el panel administrativo"). */
export function usarListarProductosAdmin(filtro: () => FiltroProductosAdmin) {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);

  return injectQuery(() => ({
    queryKey: claveListaProductosAdmin(filtro()),
    queryFn: (): Promise<ProductosPaginadosAdmin> => repositorio.listar(filtro()),
    staleTime: 15_000,
  }));
}
