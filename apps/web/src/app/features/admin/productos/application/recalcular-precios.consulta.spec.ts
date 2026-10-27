import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { PrecioPorRecalcular } from '../domain/producto-admin.model';
import {
  REPOSITORIO_RECALCULO_PRECIOS,
  RepositorioRecalculoPrecios,
} from '../domain/recalculo-precios.puerto';
import {
  CLAVE_PRECIOS_POR_RECALCULAR,
  usarPreciosPorRecalcular,
  usarRecalcularPrecios,
} from './recalcular-precios.consulta';

const BOLSO: PrecioPorRecalcular = {
  varianteId: 'v1',
  sku: 'TS-BOLSO-NEGRO',
  nombreProducto: 'Bolso tote',
  precioContado: 230000,
  publicadoActual: 239000,
  publicadoNuevo: 240000,
};

/** Como el servidor: tras aplicar, la previsualización queda vacía. */
class RepositorioRecalculoFalso implements RepositorioRecalculoPrecios {
  pendientes: PrecioPorRecalcular[] = [BOLSO];
  aplicaciones = 0;

  async previsualizar(): Promise<readonly PrecioPorRecalcular[]> {
    return this.pendientes;
  }

  async aplicar(): Promise<number> {
    this.aplicaciones++;
    const cuantas = this.pendientes.length;
    this.pendientes = [];
    return cuantas;
  }
}

function montar() {
  const repositorio = new RepositorioRecalculoFalso();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_RECALCULO_PRECIOS, useValue: repositorio },
    ],
  });
  const consulta = TestBed.runInInjectionContext(() => usarPreciosPorRecalcular());
  const mutacion = TestBed.runInInjectionContext(() => usarRecalcularPrecios());
  return { repositorio, queryClient, consulta, mutacion };
}

describe('recálculo de precios publicados', () => {
  it('la consulta trae las variantes desactualizadas bajo su llave', async () => {
    const { consulta, queryClient } = montar();

    await vi.waitFor(() => expect(consulta.data()).toEqual([BOLSO]));
    expect(queryClient.getQueryData(CLAVE_PRECIOS_POR_RECALCULAR)).toEqual([BOLSO]);
  });

  /**
   * Tras aplicar, la lista se vuelve a pedir —tiene que quedar vacía— y se invalidan los
   * productos del panel y el catálogo público, que anuncian el publicado: sin eso, la vitrina
   * seguiría con el precio viejo hasta caducar.
   */
  it('aplicar devuelve cuántas cambió e invalida la lista, el panel y el catálogo', async () => {
    const { consulta, mutacion, queryClient, repositorio } = montar();
    await vi.waitFor(() => expect(consulta.data()).toEqual([BOLSO]));
    const invalidar = vi.spyOn(queryClient, 'invalidateQueries');

    const recalculadas = await mutacion.mutateAsync();

    expect(recalculadas).toBe(1);
    expect(repositorio.aplicaciones).toBe(1);
    const llaves = invalidar.mock.calls.map(([filtros]) => filtros?.queryKey);
    expect(llaves).toEqual(
      expect.arrayContaining([CLAVE_PRECIOS_POR_RECALCULAR, ['admin', 'productos'], ['catalogo']]),
    );
    await vi.waitFor(() => expect(consulta.data()).toEqual([]));
  });
});
