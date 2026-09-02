import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_CATEGORIAS, RepositorioCategorias } from '../domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../domain/repositorio-marcas.puerto';

/**
 * Categorías y marcas para poblar los filtros. No cambian durante la sesión
 * de un visitante — staleTime largo, sin refetch agresivo.
 */
const STALE_TIME_OPCIONES = 5 * 60_000;

function opcionesCategorias(repositorio: RepositorioCategorias) {
  return {
    queryKey: ['catalogo', 'categorias'] as const,
    queryFn: () => repositorio.listarTodas(),
    staleTime: STALE_TIME_OPCIONES,
  };
}

function opcionesMarcas(repositorio: RepositorioMarcas) {
  return {
    queryKey: ['catalogo', 'marcas'] as const,
    queryFn: () => repositorio.listarTodas(),
    staleTime: STALE_TIME_OPCIONES,
  };
}

export function usarOpcionesFiltro() {
  const repositorioCategorias = inject(REPOSITORIO_CATEGORIAS);
  const repositorioMarcas = inject(REPOSITORIO_MARCAS);

  const categorias = injectQuery(() => opcionesCategorias(repositorioCategorias));
  const marcas = injectQuery(() => opcionesMarcas(repositorioMarcas));

  return { categorias, marcas };
}

/**
 * Mismo problema que `precargarProductos`: sin esto, las opciones de
 * categoría y marca llegan vacías en el HTML de SSR (la consulta todavía no
 * resolvió cuando Angular serializa) y solo aparecen tras hidratar.
 */
export function precargarOpcionesFiltro(): Promise<void> {
  const repositorioCategorias = inject(REPOSITORIO_CATEGORIAS);
  const repositorioMarcas = inject(REPOSITORIO_MARCAS);
  const queryClient = inject(QueryClient);

  return Promise.all([
    queryClient.prefetchQuery(opcionesCategorias(repositorioCategorias)),
    queryClient.prefetchQuery(opcionesMarcas(repositorioMarcas)),
  ]).then(() => undefined);
}
