import { inject } from '@angular/core';
import { injectQuery, keepPreviousData } from '@tanstack/angular-query-experimental';
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
    // Al pasar de página la llave cambia, y sin esto la consulta volvía a `pending`: la plantilla
    // pintaba el esqueleto, el paginador desaparecía con el botón pulsado dentro y el foco caía
    // en `<body>`. Con los datos de la página anterior a la vista mientras llega la nueva, el
    // paginador no se desmonta.
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  }));
}
