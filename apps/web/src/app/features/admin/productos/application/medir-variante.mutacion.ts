import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { MedirVarianteAdmin, VarianteMedida } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';
import { CLAVE_MEDIDAS } from './listar-medidas.consulta';
import { CLAVE_VARIANTES_SIN_MEDIR } from './listar-variantes-sin-medir.consulta';

export function usarMedirVariante() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: MedirVarianteAdmin): Promise<VarianteMedida> =>
      repositorio.medirVariante(comando),
    // Dos consultas miran esto y medir cambia lo que enseñan las dos: la de sin-medir alimenta el
    // aviso del panel, que baja; la de medidas, la pantalla, donde la fila se queda con las cifras
    // nuevas —o sale, con el filtro de lo que falta puesto—. Por eso se invalidan ambas y no solo
    // la que tenía el formulario abierto.
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_VARIANTES_SIN_MEDIR });
      await queryClient.invalidateQueries({ queryKey: CLAVE_MEDIDAS });
    },
  }));
}
