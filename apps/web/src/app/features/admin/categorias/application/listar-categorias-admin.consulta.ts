import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_CATEGORIAS_ADMIN } from '../domain/repositorio-categorias-admin.puerto';

export const CLAVE_CATEGORIAS_ADMIN = ['admin', 'categorias'] as const;

/**
 * Llave propia y no la `['catalogo','categorias']` del formulario de producto, por lo mismo que en
 * marcas: aquella la comparten la vitrina y el panel con adaptadores distintos, así que lo que
 * devuelve depende de quién la haya pedido primero. Esta pantalla pregunta siempre lo mismo.
 */
export function usarCategoriasAdmin() {
  const repositorio = inject(REPOSITORIO_CATEGORIAS_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_CATEGORIAS_ADMIN,
    queryFn: () => repositorio.listarTodas(),
    // Corto: quien acaba de mover una categoría espera ver el árbol nuevo.
    staleTime: 5_000,
  }));
}
