import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { TandaDeProductosEliminados } from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_NO_PUBLICADOS,
  RepositorioProductosNoPublicados,
} from '../../domain/productos-no-publicados.puerto';
import { LimpiarNoPublicados } from './limpiar-no-publicados';

/**
 * Como el servidor: borradores en orden, tandas con cursor, y los vendidos se quedan sin volver a
 * salir en la tanda siguiente.
 */
class NoPublicadosFalso implements RepositorioProductosNoPublicados {
  tanda = 2;
  readonly cursores: (string | null)[] = [];
  readonly topes: (string | null)[] = [];

  constructor(
    private ids: string[],
    private readonly vendidos: string[] = [],
  ) {}

  async contar(): Promise<number> {
    return this.ids.length;
  }

  async eliminarTanda(
    desde: string | null,
    hasta: string | null,
  ): Promise<TandaDeProductosEliminados> {
    this.cursores.push(desde);
    this.topes.push(hasta);
    const inicio = desde === null ? 0 : this.ids.indexOf(desde) + 1;
    const lote = this.ids.slice(inicio, inicio + this.tanda);
    const conservados = lote.filter((id) => this.vendidos.includes(id));
    this.ids = this.ids.filter((id) => !lote.includes(id) || conservados.includes(id));
    return {
      eliminados: lote.length - conservados.length,
      conservadosPorVentas: conservados.length,
      conservadosPorExistencias: 0,
      siguiente: lote.length < this.tanda ? null : lote[lote.length - 1],
      hasta: hasta ?? this.ids[this.ids.length - 1] ?? null,
    };
  }
}

async function renderBloque(repositorio: RepositorioProductosNoPublicados) {
  const resultado = await render(LimpiarNoPublicados, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_PRODUCTOS_NO_PUBLICADOS, useValue: repositorio },
    ],
  });
  return resultado;
}

async function abrirYConfirmar() {
  fireEvent.click(await screen.findByRole('button', { name: esAdmin.productos.limpiar.accion }));
  const caja = within(screen.getByRole('group'));
  fireEvent.click(caja.getByRole('button', { name: esAdmin.productos.limpiar.confirmar }));
}

describe('LimpiarNoPublicados', () => {
  it('dice cuántos hay y los borra siguiendo el cursor hasta terminar', async () => {
    const repositorio = new NoPublicadosFalso(['a', 'b', 'c', 'd', 'e']);
    await renderBloque(repositorio);

    expect(await screen.findByText(/Hay 5 producto\(s\) sin publicar/)).toBeTruthy();
    await abrirYConfirmar();

    expect(await screen.findByText('Se borraron 5 producto(s) no publicados.')).toBeTruthy();
    expect(repositorio.cursores).toEqual([null, 'b', 'd']);
    // El tope lo fija la primera tanda y las demás lo repiten tal cual.
    expect(repositorio.topes).toEqual([null, 'e', 'e']);
    expect(screen.queryByRole('group')).toBeNull();
  });

  it('dice cuántos se conservaron por tener ventas', async () => {
    const repositorio = new NoPublicadosFalso(['a', 'b', 'c'], ['b']);
    await renderBloque(repositorio);

    await abrirYConfirmar();

    expect(
      await screen.findByText(
        'Se borraron 2 producto(s) no publicados. 1 se conservaron porque tienen ventas o unidades en inventario.',
      ),
    ).toBeTruthy();
    expect(repositorio.cursores).toEqual([null, 'b']);
  });

  it('sin productos en borrador no ofrece nada', async () => {
    const repositorio = new NoPublicadosFalso([]);
    const contar = vi.spyOn(repositorio, 'contar');
    await renderBloque(repositorio);

    await vi.waitFor(() => expect(contar).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: esAdmin.productos.limpiar.accion })).toBeNull();
  });

  it('cancelar cierra la pregunta sin borrar', async () => {
    const repositorio = new NoPublicadosFalso(['a']);
    await renderBloque(repositorio);

    fireEvent.click(await screen.findByRole('button', { name: esAdmin.productos.limpiar.accion }));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.limpiar.cancelar }));

    expect(screen.queryByRole('group')).toBeNull();
    expect(repositorio.cursores).toEqual([]);
  });

  it('si falla lo dice y deja la pregunta abierta', async () => {
    const repositorio = new NoPublicadosFalso(['a']);
    vi.spyOn(repositorio, 'eliminarTanda').mockRejectedValue(new Error('caído'));
    await renderBloque(repositorio);

    await abrirYConfirmar();

    const alerta = await screen.findByText(esAdmin.productos.limpiar.error);
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(screen.getByRole('group')).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad con la pregunta abierta', async () => {
    const { container } = await renderBloque(new NoPublicadosFalso(['a']));

    fireEvent.click(await screen.findByRole('button', { name: esAdmin.productos.limpiar.accion }));
    await esperarSinViolaciones(container);
  });
});
