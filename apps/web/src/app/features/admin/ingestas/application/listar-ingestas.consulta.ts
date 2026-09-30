import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { FiltroLotes, loteAbierto, LotesPaginados } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';

export const CLAVE_INGESTAS_ADMIN = ['admin', 'ingestas'] as const;

export function claveListaIngestas(filtro: FiltroLotes) {
  return [...CLAVE_INGESTAS_ADMIN, filtro] as const;
}

/** Cada cuánto se vuelve a preguntar mientras haya un lote corriendo. */
export const INTERVALO_MIENTRAS_CORRE = 4_000;

/**
 * Los lotes, por página, y **refrescándose solos mientras alguno esté abierto**: la ingesta corre
 * en segundo plano en el servidor y quien acaba de subir la exportación se queda mirando la fila
 * pasar de RECIBIDO a PROCESANDO a TERMINADO. En cuanto no queda ninguno abierto, el sondeo se
 * apaga: una lista de lotes terminados no cambia sola.
 */
export function usarListarIngestas(filtro: () => FiltroLotes) {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);

  return injectQuery(() => ({
    queryKey: claveListaIngestas(filtro()),
    queryFn: (): Promise<LotesPaginados> => repositorio.listar(filtro()),
    staleTime: 5_000,
    refetchInterval: (consulta) => {
      const datos = consulta.state.data as LotesPaginados | undefined;
      return datos?.items.some(loteAbierto) ? INTERVALO_MIENTRAS_CORRE : false;
    },
  }));
}
