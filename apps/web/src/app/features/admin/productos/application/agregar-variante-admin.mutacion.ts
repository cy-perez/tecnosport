import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { AgregarVarianteAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function usarAgregarVarianteAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: AgregarVarianteAdmin): Promise<void> => repositorio.agregarVariante(comando),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: ['admin', 'productos', variables.productoId] }),
  }));
}
