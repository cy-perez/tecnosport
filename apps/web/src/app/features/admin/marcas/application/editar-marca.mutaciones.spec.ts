import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import {
  REPOSITORIO_MARCAS_ADMIN,
  RepositorioMarcasAdmin,
} from '../domain/repositorio-marcas-admin.puerto';
import { usarEliminarMarca, usarRenombrarMarca } from './editar-marca.mutaciones';
import { CLAVE_MARCAS_ADMIN } from './listar-marcas-admin.consulta';

function repositorioQueFalla(error: Error): RepositorioMarcasAdmin {
  return {
    listarTodas: () => Promise.reject(new Error('no usado')),
    crear: () => Promise.reject(new Error('no usado')),
    renombrar: () => Promise.reject(error),
    eliminar: () => Promise.reject(error),
  };
}

function montar(error: Error) {
  const queryClient = new QueryClient();
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_MARCAS_ADMIN, useValue: repositorioQueFalla(error) },
    ],
  });
  const invalidar = vi.spyOn(queryClient, 'invalidateQueries');
  const llaves = () => invalidar.mock.calls.map(([filtros]) => filtros?.queryKey);
  return {
    renombrar: TestBed.runInInjectionContext(() => usarRenombrarMarca()),
    eliminar: TestBed.runInInjectionContext(() => usarEliminarMarca()),
    llaves,
  };
}

/**
 * Otra pestaña la borró: reintentar daría 404 para siempre. La lista se vuelve a pedir desde la
 * mutación y no desde la página, que no inyecta `QueryClient` ni conoce las llaves.
 */
describe('mutaciones de marca que fallan', () => {
  it('renombrar una marca que ya no existe vuelve a pedir las marcas', async () => {
    const { renombrar, llaves } = montar(new ErrorHttp(404, 'no existe'));

    await expect(renombrar.mutateAsync({ id: 'm1', nombre: 'Nueva' })).rejects.toThrow();

    expect(llaves()).toEqual(expect.arrayContaining([CLAVE_MARCAS_ADMIN]));
  });

  it('eliminar una marca que ya no existe vuelve a pedir las marcas', async () => {
    const { eliminar, llaves } = montar(new ErrorHttp(404, 'no existe'));

    await expect(eliminar.mutateAsync('m1')).rejects.toThrow();

    expect(llaves()).toEqual(expect.arrayContaining([CLAVE_MARCAS_ADMIN]));
  });

  it('eliminar una marca con productos no invalida nada', async () => {
    const { eliminar, llaves } = montar(new ErrorHttp(409, 'con productos', 'MARCA_CON_PRODUCTOS'));

    await expect(eliminar.mutateAsync('m1')).rejects.toThrow();

    expect(llaves()).toEqual([]);
  });
});
