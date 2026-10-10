import { inject } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  keepPreviousData,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { ArchivosDeIngestaPaginados } from '../domain/archivo-de-ingesta.model';
import { REPOSITORIO_ARCHIVOS_DE_INGESTA } from '../domain/repositorio-archivos-de-ingesta.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

/**
 * Bajo el prefijo de ingestas: eliminar un lote invalida ese prefijo, y un lote eliminado se lleva
 * su zip del historial.
 */
export const CLAVE_ARCHIVOS_DE_INGESTA = [...CLAVE_INGESTAS_ADMIN, 'archivos'] as const;

export function usarArchivosDeIngesta(pagina: () => number) {
  const repositorio = inject(REPOSITORIO_ARCHIVOS_DE_INGESTA);

  return injectQuery(() => ({
    queryKey: [...CLAVE_ARCHIVOS_DE_INGESTA, pagina()] as const,
    queryFn: (): Promise<ArchivosDeIngestaPaginados> => repositorio.listar(pagina()),
    // Como en las demás listas del panel: el paginador no se desmonta al cambiar de página.
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  }));
}

/** Invalida también si falla: un 409 dice que algo cambió, y la lista tiene que verlo. */
export function usarBorrarArchivoDeIngesta() {
  const repositorio = inject(REPOSITORIO_ARCHIVOS_DE_INGESTA);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.borrar(id),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: CLAVE_ARCHIVOS_DE_INGESTA }),
  }));
}
