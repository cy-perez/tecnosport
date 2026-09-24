import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MedidasDelCatalogo } from '../domain/producto-admin.model';
import {
  PRECARGA_NO_BLOQUEANTE,
  sinBloquearLaNavegacion,
} from '../../../../core/consultas/precarga';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../domain/repositorio-productos-admin.puerto';

export const CLAVE_MEDIDAS = ['admin', 'variantes', 'medidas'] as const;

/**
 * Consulta propia y no la de sin-medir con un filtro menos: son dos listas distintas con dos
 * conteos distintos, y compartir la caché haría que la pantalla de corrección y el aviso del panel
 * se pisaran el dato. La mutación de medir invalida las dos, que es lo correcto — medir cambia lo
 * que cada una enseña.
 */
export function opcionesMedidas(repositorio: RepositorioProductosAdmin) {
  return {
    queryKey: CLAVE_MEDIDAS,
    queryFn: (): Promise<MedidasDelCatalogo> => repositorio.listarMedidas(),
    // Corto, igual que la de sin-medir: quien acaba de corregir una medida espera ver la cifra
    // nueva en la fila al volver.
    staleTime: 5_000,
  };
}

export function usarMedidas() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  return injectQuery(() => opcionesMedidas(repositorio));
}

export function precargarMedidas(queryClient: QueryClient, repositorio: RepositorioProductosAdmin) {
  return sinBloquearLaNavegacion(
    queryClient.prefetchQuery({ ...opcionesMedidas(repositorio), ...PRECARGA_NO_BLOQUEANTE }),
  );
}
