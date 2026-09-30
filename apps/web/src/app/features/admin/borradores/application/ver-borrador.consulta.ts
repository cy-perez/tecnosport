import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { BorradorDetalle } from '../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../domain/repositorio-borradores-admin.puerto';
import { CLAVE_BORRADORES_ADMIN } from './listar-borradores.consulta';

/**
 * El detalle trae URL firmadas de las fotos, que caducan: por eso el `staleTime` es corto y se
 * vuelve a pedir al volver a la pestaña, en vez de servir de caché una foto que ya no abre.
 */
export function usarVerBorrador(id: () => string) {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);

  return injectQuery(() => ({
    queryKey: [...CLAVE_BORRADORES_ADMIN, id()] as const,
    queryFn: (): Promise<BorradorDetalle> => repositorio.obtener(id()),
    enabled: id() !== '',
    staleTime: 60_000,
  }));
}
