import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { FiltroProductos } from '../../domain/filtro-productos.model';
import { Categoria, Producto } from '../../domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../domain/repositorio-categorias.puerto';
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

class RepositorioCategoriasFalso implements RepositorioCategorias {
  constructor(private readonly categorias: Categoria[]) {}

  async listarTodas(): Promise<Categoria[]> {
    return this.categorias;
  }
}

const TRES_LINEAS: Categoria[] = [
  { id: 'c0', nombre: 'Ropa deportiva', slug: 'ropa-deportiva', linea: 'ROPA_Y_CALZADO' },
  { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
  { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA' },
];

async function renderPortada(
  repositorio = new RepositorioProductosFalso(),
  categorias: Categoria[] = TRES_LINEAS,
) {
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
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso(categorias) },
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

  /**
   * El titular de la banda es el h1 de la portada, y sale de Transloco. Importa porque el archivo
   * que entregó diseño traía ese mismo titular <b>incrustado en los píxeles de la imagen</b>: ahí no
   * lo traduce nadie, no lo lee un lector de pantalla y no es un encabezado para nada.
   */
  it('el titular de la banda es texto de verdad, no parte de la imagen', async () => {
    await renderPortada();

    const titulo = screen.getByRole('heading', { level: 1 });
    expect(titulo.textContent?.trim()).toBe('Todo lo que necesitas para moverte.');
  });

  /** Y el segundo botón lleva a tecnología, que es la línea que existe: CELULARES dejó de serlo. */
  it('la banda ofrece el catálogo completo y la línea de tecnología', async () => {
    await renderPortada();

    expect(screen.getByRole('link', { name: 'Ver tecnología' }).getAttribute('href')).toBe(
      '/es/productos?linea=TECNOLOGIA',
    );
  });

  /**
   * La foto de la banda es la candidata a LCP, así que es la única con `priority` — y las cuatro
   * novedades, que lo llevaban cuando la portada no tenía imagen de hero, lo pierden. Priorizar
   * cinco imágenes es no priorizar ninguna.
   */
  it('solo la foto de la banda va priorizada', async () => {
    const { container } = await renderPortada();

    const conPrioridad = container.querySelectorAll('img[fetchpriority="high"]');
    expect(conPrioridad).toHaveLength(1);
    expect(conPrioridad[0].getAttribute('alt')).toContain('bolsos');
  });

  it('cada línea de negocio lleva al catálogo ya filtrado', async () => {
    await renderPortada();

    // `findBy*` y no `getBy*`: las baldosas ya no salen de una constante, salen de la consulta de
    // categorías, y `whenStable()` no espera a que TanStack Query resuelva (apps/web/CLAUDE.md).
    expect((await screen.findByRole('link', { name: 'Ropa y calzado' })).getAttribute('href')).toBe(
      '/es/productos?linea=ROPA_Y_CALZADO',
    );
    expect((await screen.findByRole('link', { name: 'Bolsos' })).getAttribute('href')).toBe(
      '/es/productos?linea=BOLSOS',
    );
    expect((await screen.findByRole('link', { name: 'Tecnología' })).getAttribute('href')).toBe(
      '/es/productos?linea=TECNOLOGIA',
    );
  });

  it('una línea sin categorías con productos no se ofrece', async () => {
    await renderPortada(new RepositorioProductosFalso(), [
      { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA' },
    ]);

    await screen.findByRole('link', { name: 'Tecnología' });

    // Enlazar a una rejilla vacía desde la portada es peor que en el filtro: es la primera
    // pantalla del sitio, y quien la abre lee "se agotó" donde dice "no vendemos eso".
    expect(screen.queryByRole('link', { name: 'Ropa y calzado' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Bolsos' })).toBeNull();
  });

  it('sin ninguna línea con productos, la sección entera desaparece', async () => {
    await renderPortada(new RepositorioProductosFalso(), []);

    // El estado de una tienda recién desplegada, antes de publicar el primer producto. Un
    // encabezado "Nuestras líneas" con nada debajo informa peor que no estar.
    expect(screen.queryByRole('heading', { name: 'Nuestras líneas' })).toBeNull();
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
        { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso(TRES_LINEAS) },
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
        { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso(TRES_LINEAS) },
      ],
    });

    expect(await screen.findByText('Todavía no hay productos publicados.')).toBeTruthy();
  });
});
