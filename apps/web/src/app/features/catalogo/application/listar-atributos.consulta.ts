import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_ATRIBUTOS } from '../domain/repositorio-atributos.puerto';

/** Catálogo global de atributos, no cambia durante la sesión — mismo staleTime largo que
 * `listar-opciones-filtro.consulta.ts`. Usado hoy solo por el panel admin al armar una variante. */
const STALE_TIME_ATRIBUTOS = 5 * 60_000;

export function usarAtributos() {
  const repositorio = inject(REPOSITORIO_ATRIBUTOS);

  return injectQuery(() => ({
    queryKey: ['catalogo', 'atributos'] as const,
    queryFn: () => repositorio.listarTodas(),
    staleTime: STALE_TIME_ATRIBUTOS,
  }));
}
