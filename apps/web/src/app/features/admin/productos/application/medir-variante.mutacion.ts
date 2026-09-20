import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { MedirVarianteAdmin, VarianteMedida } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';
import { CLAVE_VARIANTES_SIN_MEDIR } from './listar-variantes-sin-medir.consulta';

export function usarMedirVariante() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: MedirVarianteAdmin): Promise<VarianteMedida> =>
      repositorio.medirVariante(comando),
    // La lista y el conteo son la misma consulta, así que una sola invalidación los corrige los
    // dos: la fila medida desaparece y el aviso del panel baja.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CLAVE_VARIANTES_SIN_MEDIR }),
  }));
}
