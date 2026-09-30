import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { BorradoresPaginados, FiltroBorradores } from '../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../domain/repositorio-borradores-admin.puerto';

export const CLAVE_BORRADORES_ADMIN = ['admin', 'borradores'] as const;

export function claveListaBorradores(filtro: FiltroBorradores) {
  return [...CLAVE_BORRADORES_ADMIN, filtro] as const;
}

/** Por página, no scroll infinito: es el panel (docs/03-api.md). */
export function usarListarBorradores(filtro: () => FiltroBorradores) {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);

  return injectQuery(() => ({
    queryKey: claveListaBorradores(filtro()),
    queryFn: (): Promise<BorradoresPaginados> => repositorio.listar(filtro()),
    staleTime: 15_000,
  }));
}
