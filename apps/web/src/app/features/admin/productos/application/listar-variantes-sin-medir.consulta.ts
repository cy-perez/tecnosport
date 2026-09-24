import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { InventarioSinMedir } from '../domain/producto-admin.model';
import {
  PRECARGA_NO_BLOQUEANTE,
  sinBloquearLaNavegacion,
} from '../../../../core/consultas/precarga';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../domain/repositorio-productos-admin.puerto';

export const CLAVE_VARIANTES_SIN_MEDIR = ['admin', 'variantes', 'sin-medir'] as const;

/**
 * Una sola función de opciones para las dos entradas —el aviso del panel y la pantalla de la
 * lista—, que es lo que pide `ADR-0011`: repetir `queryKey`/`queryFn` en cada sitio es como acaban
 * dos pantallas mirando cachés distintas del mismo dato.
 */
export function opcionesVariantesSinMedir(repositorio: RepositorioProductosAdmin) {
  return {
    queryKey: CLAVE_VARIANTES_SIN_MEDIR,
    queryFn: (): Promise<InventarioSinMedir> => repositorio.listarSinMedir(),
    // Corto a propósito: el número es una cuenta pendiente, y quien acaba de medir una variante
    // espera verlo bajar al volver al panel.
    staleTime: 5_000,
  };
}

export function usarVariantesSinMedir() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  return injectQuery(() => opcionesVariantesSinMedir(repositorio));
}

export function precargarVariantesSinMedir(
  queryClient: QueryClient,
  repositorio: RepositorioProductosAdmin,
) {
  return sinBloquearLaNavegacion(
    queryClient.prefetchQuery({
      ...opcionesVariantesSinMedir(repositorio),
      ...PRECARGA_NO_BLOQUEANTE,
    }),
  );
}
