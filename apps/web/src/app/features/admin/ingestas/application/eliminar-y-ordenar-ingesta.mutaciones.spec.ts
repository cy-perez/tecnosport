import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { LoteEliminado, LoteIngesta } from '../domain/ingesta.model';
import {
  REPOSITORIO_INGESTAS_ADMIN,
  RepositorioIngestasAdmin,
} from '../domain/repositorio-ingestas-admin.puerto';
import { usarEliminarIngesta } from './eliminar-ingesta.mutacion';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';
import { usarOrdenarIngesta } from './ordenar-ingesta.mutacion';

/** Solo falla: lo que se prueba aquí es qué se invalida cuando la escritura no pasa. */
function repositorioQueFalla(error: Error): RepositorioIngestasAdmin {
  return {
    listar: () => Promise.reject(new Error('no usado')),
    obtener: () => Promise.reject(new Error('no usado')),
    subir: () => Promise.reject(new Error('no usado')),
    eliminar: (): Promise<LoteEliminado> => Promise.reject(error),
    ordenar: (): Promise<LoteIngesta> => Promise.reject(error),
    publicacionesCerradas: () => Promise.reject(new Error('no usado')),
  };
}

function montar(error: Error) {
  const queryClient = new QueryClient();
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_INGESTAS_ADMIN, useValue: repositorioQueFalla(error) },
    ],
  });
  const invalidar = vi.spyOn(queryClient, 'invalidateQueries');
  const llaves = () => invalidar.mock.calls.map(([filtros]) => filtros?.queryKey);
  return {
    eliminar: TestBed.runInInjectionContext(() => usarEliminarIngesta()),
    ordenar: TestBed.runInInjectionContext(() => usarOrdenarIngesta()),
    llaves,
  };
}

/**
 * La invalidación al fallar vive en la mutación y no en la página: la página no inyecta
 * `QueryClient` ni conoce las llaves (capa `presentation`). Estas pruebas son las que lo sostienen.
 */
describe('mutaciones de ingesta que fallan', () => {
  it('eliminar una ingesta que ya no existe (404) vuelve a pedir la lista', async () => {
    const { eliminar, llaves } = montar(new ErrorHttp(404, 'no existe'));

    await expect(eliminar.mutateAsync('lote-1')).rejects.toThrow();

    expect(llaves()).toEqual([CLAVE_INGESTAS_ADMIN]);
  });

  it('eliminar que falla por otra cosa no invalida nada', async () => {
    const { eliminar, llaves } = montar(new ErrorHttp(409, 'en curso', 'LOTE_EN_CURSO'));

    await expect(eliminar.mutateAsync('lote-1')).rejects.toThrow();

    expect(llaves()).toEqual([]);
  });

  it('una orden que falla vuelve a pedir la lista, para que los botones digan la verdad', async () => {
    const { ordenar, llaves } = montar(new ErrorHttp(409, 'ya terminó'));

    await expect(ordenar.mutateAsync({ id: 'lote-1', orden: 'pausar' })).rejects.toThrow();

    expect(llaves()).toEqual([CLAVE_INGESTAS_ADMIN]);
  });
});
