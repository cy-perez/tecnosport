import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Categoria, Marca } from '../../domain/producto.model';
import { REPOSITORIO_CATEGORIAS, RepositorioCategorias } from '../../domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../../domain/repositorio-marcas.puerto';
import { FiltrosProductos } from './filtros-productos';

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [
      { nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
      { nombre: 'Celulares', slug: 'celulares', linea: 'CELULARES' },
    ];
  }
}

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [{ id: 'm1', nombre: 'TecnoSport' }];
  }
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function renderFiltros() {
  return render(FiltrosProductos, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
      { provide: REPOSITORIO_MARCAS, useClass: RepositorioMarcasFalso },
    ],
  });
}

describe('FiltrosProductos', () => {
  it('elegir una línea navega con ese query param, con debounce', async () => {
    const { fixture } = await renderFiltros();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.change(await screen.findByLabelText('Línea'), { target: { value: 'BOLSOS' } });
    expect(navegar).not.toHaveBeenCalled();

    await esperar(350);

    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { linea: 'BOLSOS' } }));
  });

  it('"Limpiar filtros" navega sin query params, sin esperar el debounce', async () => {
    const { fixture } = await renderFiltros();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(await screen.findByText('Limpiar filtros'));

    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));
  });
});
