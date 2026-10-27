import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { PrecioPorRecalcular, ResultadoRecalculo } from '../../domain/producto-admin.model';
import {
  REPOSITORIO_RECALCULO_PRECIOS,
  RepositorioRecalculoPrecios,
} from '../../domain/recalculo-precios.puerto';
import { PreciosAdminPage } from './precios-admin.page';

const t = esAdmin.productos.precios;

function precio(overrides: Partial<PrecioPorRecalcular> = {}): PrecioPorRecalcular {
  return {
    varianteId: 'v1',
    sku: 'TS-BOLSO-NEGRO',
    nombreProducto: 'Bolso tote',
    precioContado: 230000,
    publicadoActual: 239000,
    publicadoNuevo: 241000,
    ...overrides,
  };
}

/** Como el servidor: tras aplicar, la previsualización queda vacía. */
class RepositorioRecalculoFalso implements RepositorioRecalculoPrecios {
  aplicaciones = 0;
  falloAlAplicar: unknown = null;
  recibidas: readonly PrecioPorRecalcular[] = [];
  /** Las que el servidor encontraría cambiadas entre la revisión y la aplicación. */
  cambiadas = new Set<string>();

  constructor(private pendientes: readonly PrecioPorRecalcular[]) {}

  async previsualizar(): Promise<readonly PrecioPorRecalcular[]> {
    return this.pendientes;
  }

  async aplicar(revisadas: readonly PrecioPorRecalcular[]): Promise<ResultadoRecalculo> {
    this.aplicaciones++;
    if (this.falloAlAplicar) {
      throw this.falloAlAplicar;
    }
    this.recibidas = revisadas;
    const omitidas = revisadas.filter((r) => this.cambiadas.has(r.varianteId));
    // Las omitidas siguen desalineadas: la lista, vuelta a pedir, las vuelve a enseñar.
    this.pendientes = omitidas;
    return { recalculadas: revisadas.length - omitidas.length, omitidas: omitidas.length };
  }
}

async function renderPagina(pendientes: readonly PrecioPorRecalcular[]) {
  const repositorio = new RepositorioRecalculoFalso(pendientes);
  const resultado = await render(PreciosAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_RECALCULO_PRECIOS, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

const DOS = [
  precio(),
  precio({
    varianteId: 'v2',
    sku: 'TS-MOTO-G17',
    nombreProducto: 'Moto G17',
    precioContado: 1000000,
    publicadoActual: 1034000,
    publicadoNuevo: 1040000,
  }),
];

const botonRecalcular = (total: number) =>
  screen.findByRole('button', { name: t.recalcular.replace('{{total}}', String(total)) });

describe('PreciosAdminPage', () => {
  it('lista cada variante con su contado y el publicado de hoy y el nuevo', async () => {
    const { container } = await renderPagina(DOS);

    expect(await screen.findByText('Bolso tote')).toBeTruthy();
    expect(screen.getByText(t.resumen.replace('{{total}}', '2'))).toBeTruthy();
    const fila = within(screen.getByText('TS-MOTO-G17').closest('tr') as HTMLElement);
    expect(fila.getByText('$ 1.000.000')).toBeTruthy();
    expect(fila.getByText('$ 1.034.000')).toBeTruthy();
    expect(fila.getByText('$ 1.040.000')).toBeTruthy();
    expect(await botonRecalcular(2)).toBeTruthy();
    await esperarSinViolaciones(container);
  });

  it('con todo al día lo dice y no ofrece recalcular', async () => {
    await renderPagina([]);

    expect(await screen.findByText(t.alDia)).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('button', { name: /Recalcular/ })).toBeNull();
  });

  /** Un precio anunciado no cambia sin que alguien lo confirme; cancelar no manda nada. */
  it('pide confirmación antes de aplicar, y cancelar no aplica', async () => {
    const { repositorio } = await renderPagina(DOS);

    const boton = await botonRecalcular(2);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(boton);

    const caja = await screen.findByRole('group', {
      name: t.pregunta.replace('{{total}}', '2'),
    });
    expect(caja.textContent).toContain(t.loQueImplica);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(repositorio.aplicaciones).toBe(0);

    fireEvent.click(within(caja).getByRole('button', { name: t.cancelar }));
    expect(screen.queryByRole('group')).toBeNull();
    expect(repositorio.aplicaciones).toBe(0);
  });

  /**
   * Confirmar aplica una sola vez, dice cuántas cambió en la región viva —que sigue en la página
   * aunque la tabla desaparezca— y la lista, vuelta a pedir, queda al día.
   */
  it('al confirmar recalcula, dice cuántas y la lista queda al día', async () => {
    const { repositorio } = await renderPagina(DOS);

    fireEvent.click(await botonRecalcular(2));
    const caja = await screen.findByRole('group');
    const confirmar = within(caja).getByRole('button', { name: t.confirmar });
    fireEvent.click(confirmar);
    fireEvent.click(confirmar);

    const estado = await screen.findByRole('status');
    await vi.waitFor(() =>
      expect(estado.textContent?.trim()).toBe(t.hecho.replace('{{recalculadas}}', '2')),
    );
    expect(repositorio.aplicaciones).toBe(1);
    expect(repositorio.recibidas).toEqual(DOS);
    expect(await screen.findByText(t.alDia)).toBeTruthy();
    expect(screen.queryByRole('group')).toBeNull();
  });

  /**
   * Entre la revisión y la confirmación alguien cambió el precio del bolso desde la ficha: el
   * servidor no lo pisa, y la pantalla dice cuántas se omitieron y que hay que revisar de nuevo.
   */
  it('si alguna cambió mientras se revisaba, dice cuántas se omitieron y vuelve a listarlas', async () => {
    const { repositorio } = await renderPagina(DOS);
    repositorio.cambiadas.add('v1');

    fireEvent.click(await botonRecalcular(2));
    const caja = await screen.findByRole('group');
    fireEvent.click(within(caja).getByRole('button', { name: t.confirmar }));

    const estado = await screen.findByRole('status');
    await vi.waitFor(() =>
      expect(estado.textContent?.trim()).toBe(
        t.hechoConOmitidas.replace('{{recalculadas}}', '1').replace('{{omitidas}}', '1'),
      ),
    );
    expect(await screen.findByText(t.resumen.replace('{{total}}', '1'))).toBeTruthy();
    expect(screen.getByText('TS-BOLSO-NEGRO')).toBeTruthy();
  });

  it('si falla, lo dice dentro de la confirmación y no la cierra', async () => {
    const { repositorio } = await renderPagina(DOS);
    repositorio.falloAlAplicar = new Error('HTTP 500');

    fireEvent.click(await botonRecalcular(2));
    const caja = await screen.findByRole('group');
    fireEvent.click(within(caja).getByRole('button', { name: t.confirmar }));

    expect(await within(caja).findByRole('alert')).toBeTruthy();
    expect(screen.getByRole('group')).toBeTruthy();
    expect(screen.getByText('Bolso tote')).toBeTruthy();
  });
});
