import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_PALETA_COLORES } from '../domain/repositorio-paleta-colores.puerto';

/** La paleta la siembra una migración y no cambia durante la sesión: mismo staleTime largo. */
const STALE_TIME_PALETA = 5 * 60_000;

export function usarPaletaDeColores() {
  const repositorio = inject(REPOSITORIO_PALETA_COLORES);

  return injectQuery(() => ({
    queryKey: ['catalogo', 'paleta-colores'] as const,
    queryFn: () => repositorio.listarTodos(),
    staleTime: STALE_TIME_PALETA,
  }));
}
