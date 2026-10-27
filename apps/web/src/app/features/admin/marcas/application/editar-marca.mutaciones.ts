import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import {
  REPOSITORIO_MARCAS_ADMIN,
  ResultadoRenombrarMarca,
} from '../domain/repositorio-marcas-admin.puerto';
import { CLAVE_MARCAS_ADMIN } from './listar-marcas-admin.consulta';

/**
 * Las dos llaves que cachean marcas: la de esta pantalla y la del formulario de producto y los
 * filtros (`['catalogo', 'marcas']`). Un nombre viejo en el desplegable del producto, o una marca
 * borrada que todavía se ofrece, es el mismo defecto que `usarCrearMarca` ya evita al crear.
 */
async function invalidarMarcas(queryClient: QueryClient): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: CLAVE_MARCAS_ADMIN });
  await queryClient.invalidateQueries({ queryKey: ['catalogo', 'marcas'] });
}

/**
 * Un 404 dice que otra pestaña la borró: reintentar daría 404 para siempre, así que la lista se
 * vuelve a pedir y la fila desaparece.
 */
async function invalidarSiYaNoExiste(queryClient: QueryClient, error: unknown): Promise<void> {
  if (error instanceof ErrorHttp && error.estado === 404) {
    await invalidarMarcas(queryClient);
  }
}

export interface ComandoRenombrarMarca {
  readonly id: string;
  readonly nombre: string;
}

export function usarRenombrarMarca() {
  const repositorio = inject(REPOSITORIO_MARCAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: ({ id, nombre }: ComandoRenombrarMarca): Promise<ResultadoRenombrarMarca> =>
      repositorio.renombrar(id, nombre),
    onSuccess: async (resultado: ResultadoRenombrarMarca) => {
      if (resultado.tipo === 'RENOMBRADA') {
        await invalidarMarcas(queryClient);
      }
    },
    onError: (error: unknown) => invalidarSiYaNoExiste(queryClient, error),
  }));
}

export function usarEliminarMarca() {
  const repositorio = inject(REPOSITORIO_MARCAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.eliminar(id),
    onSuccess: () => invalidarMarcas(queryClient),
    onError: (error: unknown) => invalidarSiYaNoExiste(queryClient, error),
  }));
}
