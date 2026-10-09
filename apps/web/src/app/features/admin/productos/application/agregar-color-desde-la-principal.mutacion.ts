import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { AgregarColorDesdeLaPrincipalAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/** Como agregar una variante: invalida el detalle, que es donde se ven las nuevas y la foto. */
export function usarAgregarColorDesdeLaPrincipal() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: AgregarColorDesdeLaPrincipalAdmin): Promise<void> =>
      repositorio.agregarColorDesdeLaPrincipal(comando),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: ['admin', 'productos', variables.productoId] }),
  }));
}
