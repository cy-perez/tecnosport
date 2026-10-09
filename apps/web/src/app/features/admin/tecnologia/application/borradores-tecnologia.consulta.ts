import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { BorradorTecnologia, EstadoBorradorTecnologia } from '../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../domain/repositorio-borradores-tecnologia.puerto';

export const CLAVE_BORRADORES_TECNOLOGIA = ['admin', 'borradores-tecnologia'] as const;

/** Sin paginar: una lista trae decenas de modelos, no miles. */
export function usarListarBorradoresTecnologia(estado: () => EstadoBorradorTecnologia) {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);

  return injectQuery(() => ({
    queryKey: [...CLAVE_BORRADORES_TECNOLOGIA, 'lista', estado()] as const,
    queryFn: (): Promise<BorradorTecnologia[]> => repositorio.listar(estado()),
    staleTime: 15_000,
  }));
}

export function usarVerBorradorTecnologia(id: () => string) {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);

  return injectQuery(() => ({
    queryKey: [...CLAVE_BORRADORES_TECNOLOGIA, id()] as const,
    queryFn: (): Promise<BorradorTecnologia> => repositorio.obtener(id()),
    enabled: id() !== '',
    staleTime: 15_000,
  }));
}
