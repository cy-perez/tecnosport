import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Producto } from '../domain/producto.model';
import {
  REPOSITORIO_PRODUCTOS,
  RepositorioProductos,
} from '../domain/repositorio-productos.puerto';
import { PRECARGA_NO_BLOQUEANTE, sinBloquearLaNavegacion } from '../../../core/consultas/precarga';

/**
 * Mismas opciones para el componente y para el prefetch del resolver de ruta
 * — misma queryKey, misma caché (mismo criterio que
 * buscar-productos.consulta.ts).
 */
function opcionesFicha(repositorio: RepositorioProductos, slug: string) {
  return {
    queryKey: ['catalogo', 'producto', slug] as const,
    queryFn: (): Promise<Producto | null> => repositorio.buscarPorSlug(slug),
    staleTime: 60_000,
  };
}

export function usarFichaProducto(slug: () => string) {
  const repositorio = inject(REPOSITORIO_PRODUCTOS);

  return injectQuery(() => opcionesFicha(repositorio, slug()));
}

/**
 * Calienta la caché ANTES de que exista el componente — mismo motivo que
 * `precargarProductos`: sin esto el SSR no es determinista.
 * `prefetchQuery` traga errores, así que un slug inexistente no rompe la
 * navegación: la página igual se activa y muestra su propio estado. Tragar el
 * error no basta, además tiene que **terminar** — de eso se encarga
 * `PRECARGA_NO_BLOQUEANTE`.
 */
export function precargarFichaProducto(slug: string): Promise<void> {
  const repositorio = inject(REPOSITORIO_PRODUCTOS);
  const queryClient = inject(QueryClient);

  return sinBloquearLaNavegacion(
    queryClient.prefetchQuery({ ...opcionesFicha(repositorio, slug), ...PRECARGA_NO_BLOQUEANTE }),
  );
}
