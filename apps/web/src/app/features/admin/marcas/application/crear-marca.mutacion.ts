import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import {
  REPOSITORIO_MARCAS_ADMIN,
  ResultadoCrearMarca,
} from '../domain/repositorio-marcas-admin.puerto';
import { CLAVE_MARCAS_ADMIN } from './listar-marcas-admin.consulta';

export function usarCrearMarca() {
  const repositorio = inject(REPOSITORIO_MARCAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (nombre: string): Promise<ResultadoCrearMarca> => repositorio.crear(nombre),
    onSuccess: (resultado: ResultadoCrearMarca) => {
      if (resultado.tipo !== 'CREADA') {
        return;
      }
      void queryClient.invalidateQueries({ queryKey: CLAVE_MARCAS_ADMIN });
      // Y la del formulario de producto, que es otra entrada de caché con el mismo dato dentro:
      // sin esto, la marca recién creada no sale en el desplegable hasta que aquella caduque, y
      // cargarle un producto era justamente el motivo para crearla.
      void queryClient.invalidateQueries({ queryKey: ['catalogo', 'marcas'] });
    },
  }));
}
