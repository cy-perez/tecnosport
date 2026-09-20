import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ExistenciasDelCatalogo } from '../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../domain/repositorio-productos-admin.puerto';

export const CLAVE_EXISTENCIAS = ['admin', 'variantes', 'existencias'] as const;

/**
 * Una sola función de opciones para el aviso del panel y para la pantalla, igual que la de
 * sin-medir y por lo que pide `ADR-0011`: dos sitios con su propio `queryKey` acaban mirando
 * cachés distintas del mismo dato.
 */
export function opcionesExistencias(repositorio: RepositorioProductosAdmin) {
  return {
    queryKey: CLAVE_EXISTENCIAS,
    queryFn: (): Promise<ExistenciasDelCatalogo> => repositorio.listarExistencias(),
    // Corto, como el de sin-medir: quien acaba de contar una variante espera ver bajar el número
    // de descuadradas al volver al panel.
    staleTime: 5_000,
  };
}

export function usarExistencias() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  return injectQuery(() => opcionesExistencias(repositorio));
}

export function precargarExistencias(
  queryClient: QueryClient,
  repositorio: RepositorioProductosAdmin,
) {
  return queryClient.prefetchQuery(opcionesExistencias(repositorio));
}
