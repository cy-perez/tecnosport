import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_MARCAS_ADMIN } from '../domain/repositorio-marcas-admin.puerto';

export const CLAVE_MARCAS_ADMIN = ['admin', 'marcas'] as const;

/**
 * Llave propia y no la `['catalogo','marcas']` de `usarOpcionesFiltro`, aunque el dato sea el
 * mismo: aquella la comparten la vitrina y el panel con adaptadores distintos —el público y el de
 * admin—, así que lo que devuelve depende de quién la haya pedido primero. Esta pantalla pregunta
 * siempre lo mismo.
 */
export function usarMarcasAdmin() {
  const repositorio = inject(REPOSITORIO_MARCAS_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_MARCAS_ADMIN,
    queryFn: () => repositorio.listarTodas(),
    // Corto: quien acaba de crear una marca espera verla en la lista.
    staleTime: 5_000,
  }));
}
