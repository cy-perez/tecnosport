import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Producto } from '../../domain/producto.model';
import { REPOSITORIO_PRODUCTOS, RepositorioProductos } from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { FichaPage } from './ficha.page';

function productoDePrueba(): Producto {
  return {
    slug: 'morral-urbano',
    nombre: 'Morral urbano',
    descripcion: 'Un morral resistente para el día a día.',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [{ sku: 'SKU-1', precio: { valor: 150_000, moneda: 'COP' }, existencia: 3, atributos: [] }],
  };
}

function activatedRouteConSlug(slug: string) {
  const paramMap = convertToParamMap({ slug });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderFicha(repositorio: RepositorioProductos, slug = 'morral-urbano') {
  return render(FichaPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      { provide: ActivatedRoute, useValue: activatedRouteConSlug(slug) },
    ],
  });
}

describe('FichaPage', () => {
  it('muestra el producto encontrado', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('heading', { name: 'Morral urbano' })).toBeTruthy();
    expect(screen.getByText('TecnoSport')).toBeTruthy();
  });

  it('muestra "no encontrado" cuando el repositorio devuelve null', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(null),
    };

    await renderFicha(repositorio, 'no-existe');

    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'No encontramos este producto.');
  });

  it('muestra un mensaje de error si la consulta falla', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.reject(new Error('falla de red')),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'No se pudo cargar el producto. Intenta de nuevo.',
    );
  });
});
