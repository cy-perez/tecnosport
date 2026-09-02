import { inject } from '@angular/core';
import { injectInfiniteQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { FiltroProductos } from '../domain/filtro-productos.model';
import { Producto } from '../domain/producto.model';
import { REPOSITORIO_PRODUCTOS, RepositorioProductos } from '../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../domain/resultado-paginado.model';

/**
 * Mismas opciones para el componente y para el prefetch del resolver de ruta
 * — misma queryKey, misma caché. `injectInfiniteQuery` es la herramienta
 * pensada para paginación por cursor con "cargar más" (docs/03-api.md:
 * cursor en el catálogo, página en el panel administrativo).
 */
function opcionesBusqueda(repositorio: RepositorioProductos, filtro: FiltroProductos) {
  return {
    queryKey: ['catalogo', 'productos', filtro] as const,
    queryFn: ({ pageParam }: { pageParam: string | null }): Promise<ResultadoPaginado<Producto>> =>
      repositorio.buscar(filtro, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (ultimaPagina: ResultadoPaginado<Producto>) => ultimaPagina.cursorSiguiente,
    // El catálogo no cambia a cada minuto; staleTime explícito por consulta,
    // como pide apps/web/CLAUDE.md.
    staleTime: 60_000,
  };
}

export function usarBusquedaProductos(filtro: () => FiltroProductos) {
  const repositorio = inject(REPOSITORIO_PRODUCTOS);

  return injectInfiniteQuery(() => opcionesBusqueda(repositorio, filtro()));
}

/**
 * Calienta la caché ANTES de que exista el componente — pensado para un
 * resolver de ruta. Sin esto, el SSR no es determinista: Angular puede
 * serializar la página antes de que la consulta del componente resuelva
 * (`PendingTasks` se registra desde un `effect`, que se agenda async, no
 * sincrónico con el primer render). `prefetchInfiniteQuery` nunca lanza —
 * si el backend falla, el componente igual reintenta y muestra su propio
 * estado de error.
 */
export function precargarProductos(filtro: FiltroProductos = {}): Promise<void> {
  const repositorio = inject(REPOSITORIO_PRODUCTOS);
  const queryClient = inject(QueryClient);

  return queryClient.prefetchInfiniteQuery(opcionesBusqueda(repositorio, filtro));
}
