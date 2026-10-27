import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { VariantesSinMargen } from '../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../domain/repositorio-productos-admin.puerto';

export const CLAVE_VARIANTES_SIN_MARGEN = ['admin', 'variantes', 'sin-margen'] as const;

export function opcionesVariantesSinMargen(repositorio: RepositorioProductosAdmin) {
  return {
    queryKey: CLAVE_VARIANTES_SIN_MARGEN,
    queryFn: (): Promise<VariantesSinMargen> => repositorio.listarSinMargen(),
    // Corto, como el de sin-medir: quien acaba de subir un contado espera ver bajar la cuenta.
    staleTime: 5_000,
  };
}

export function usarVariantesSinMargen() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  return injectQuery(() => opcionesVariantesSinMargen(repositorio));
}
