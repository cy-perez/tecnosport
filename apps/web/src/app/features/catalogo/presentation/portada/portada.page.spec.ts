import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { FiltroProductos } from '../../domain/filtro-productos.model';
import { Producto } from '../../domain/producto.model';
import {
  REPOSITORIO_PRODUCTOS,
  RepositorioProductos,
} from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { PortadaPage } from './portada.page';

function productoDePrueba(slug: string): Producto {
  return {
    slug,
    nombre: `Producto ${slug}`,
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [
      {
        id: `id-${slug}`,
        sku: `SKU-${slug}`,
        precio: { valor: 10_000, moneda: 'COP' },
        existencia: 5,
        atributos: [],
      },
    ],
  };
}

class RepositorioProductosFalso implements RepositorioProductos {
  filtroRecibido: FiltroProductos | null = null;

  async buscar(filtro: FiltroProductos): Promise<ResultadoPaginado<Producto>> {
    this.filtroRecibido = filtro;
    return { items: [productoDePrueba('a'), productoDePrueba('b')], cursorSiguiente: null };
  }

  async buscarPorSlug(): Promise<Producto | null> {
    return null;
  }
}

async function renderPortada(repositorio = new RepositorioProductosFalso()) {
  const resultado = await render(PortadaPage, {
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
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
    ],
  });
  await resultado.fixture.whenStable();
  return { ...resultado, repositorio };
}

describe('PortadaPage', () => {
  it('tiene un solo h1 y lleva al catálogo completo', async () => {
    await renderPortada();

    const titulos = screen.getAllByRole('heading', { level: 1 });
    expect(titulos).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Ver el catálogo' }).getAttribute('href')).toBe(
      '/es/productos',
    );
  });

  it('cada línea de negocio lleva al catálogo ya filtrado', async () => {
    await renderPortada();

    expect(screen.getByRole('link', { name: 'Ropa y calzado' }).getAttribute('href')).toBe(
      '/es/productos?linea=ROPA_Y_CALZADO',
    );
    expect(screen.getByRole('link', { name: 'Bolsos' }).getAttribute('href')).toBe(
      '/es/productos?linea=BOLSOS',
    );
    expect(screen.getByRole('link', { name: 'Celulares' }).getAttribute('href')).toBe(
      '/es/productos?linea=CELULARES',
    );
  });

  it('muestra las novedades pidiéndolas por fecha, no por relevancia', async () => {
    const { repositorio } = await renderPortada();

    expect(repositorio.filtroRecibido).toEqual({ orden: 'MAS_RECIENTES', tamano: 4 });

    const tarjeta = await screen.findByRole('link', { name: /Producto a/ });
    expect(tarjeta.getAttribute('href')).toBe('/es/productos/a');
  });

  it('con el catálogo caído muestra el error y no una franja vacía en silencio', async () => {
    const repositorioCaido: RepositorioProductos = {
      buscar: () => Promise.reject(new Error('backend caído')),
      buscarPorSlug: () => Promise.resolve(null),
    };

    await render(PortadaPage, {
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en, 'catalogo/es': esCatalogo } as never,
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([]),
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: REPOSITORIO_PRODUCTOS, useValue: repositorioCaido },
      ],
    });

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('con el catálogo vacío lo dice, en vez de dejar un hueco mudo', async () => {
    const repositorioVacio: RepositorioProductos = {
      buscar: () => Promise.resolve({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(null),
    };

    await render(PortadaPage, {
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
        { provide: REPOSITORIO_PRODUCTOS, useValue: repositorioVacio },
      ],
    });

    expect(await screen.findByText('Todavía no hay productos publicados.')).toBeTruthy();
  });
});
