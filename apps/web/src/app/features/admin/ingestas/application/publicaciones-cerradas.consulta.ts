import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PublicacionCerrada } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

/**
 * Las publicaciones que no dejaron borrador del lote que se está mirando, o nada si no se mira
 * ninguno. Cuelga de la llave de los lotes: reintentar las fallidas invalida los lotes, y con ellos
 * esta lista, que ya no es la misma.
 */
export function usarPublicacionesCerradas(loteId: () => string | null) {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);

  return injectQuery(() => ({
    queryKey: [...CLAVE_INGESTAS_ADMIN, 'cerradas', loteId()] as const,
    queryFn: (): Promise<readonly PublicacionCerrada[]> =>
      repositorio.publicacionesCerradas(loteId() ?? ''),
    enabled: loteId() !== null,
    staleTime: 30_000,
  }));
}
