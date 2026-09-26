import { DeferBlockBehavior } from '@angular/core/testing';
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
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS', padreId: null },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [
      {
        id: `id-${slug}`,
        sku: `SKU-${slug}`,
        precio: { valor: 10_000, moneda: 'COP' },
        disponible: true,
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
  { id: 'c0', nombre: 'Ropa deportiva', slug: 'ropa-deportiva', linea: 'ROPA', padreId: null },
  { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS', padreId: null },
  { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
];

async function renderPortada(
  repositorio = new RepositorioProductosFalso(),
  categorias: Categoria[] = TRES_LINEAS,
) {
  const resultado = await render(PortadaPage, {
    // La franja de novedades vive dentro de un `@defer` desde que se hidrata al
    // entrar en pantalla: sin esto `TestBed` no dispara el bloque y no hay
    // tarjetas que consultar.
    deferBlockBehavior: DeferBlockBehavior.Playthrough,
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
    expect(screen.getByRole('link', { name: 'Ver todo el catálogo' }).getAttribute('href')).toBe(
      '/es/productos',
    );
  });

  /**
   * <b>El `h1` vive fuera del carrusel</b>, y esto es una corrección: estaba en la primera
   * diapositiva, que a los cinco segundos queda `inert` y `aria-hidden`, así que la portada se
   * quedaba sin ningún encabezado de nivel uno en cuanto el carrusel avanzaba.
   *
   * <p>Es `sr-only` porque el titular grande que se ve cambia cada cinco segundos, y un `h1` que
   * cambia de texto solo es peor que uno estable. Lo que nombra a esta página no es "Vístete para
   * moverte" sino lo que la tienda vende.
   */
  it('la portada tiene un h1 estable, fuera del carrusel', async () => {
    const { container } = await renderPortada();

    const titulo = screen.getByRole('heading', { level: 1 });
    expect(titulo.textContent?.trim()).toBe('Ropa y calzado deportivo, bolsos y tecnología');
    expect(container.querySelector('ts-carrusel-hero')?.contains(titulo)).toBe(false);
  });

  /**
   * Y los titulares de las cuatro piezas siguen siendo texto de verdad, no píxeles: el arte que
   * entregó diseño los trae <b>incrustados en la imagen</b> —la carpeta `con-texto/` del ZIP—, y
   * ahí no los traduce nadie ni los lee un lector de pantalla. Se usa la pieza `limpio/`.
   */
  it('los titulares de las piezas son texto, no parte de la imagen', async () => {
    await renderPortada();

    expect(screen.getByText('Vístete para moverte')).toBeTruthy();
    expect(screen.getByText('Tenis para cada ritmo')).toBeTruthy();
  });

  /**
   * Que la banda ofrezca las cuatro líneas, cada una a su rejilla filtrada, lo comprueba
   * `ts-carrusel-hero.spec.ts` con los cuatro `href`. Aquí solo se afirma que la portada monta el
   * carrusel: repetir los cuatro destinos en las dos pruebas es dos sitios que actualizar cuando
   * cambie uno.
   *
   * Se busca **dentro** del carrusel y no en la página entera: las baldosas de "Nuestras líneas"
   * llevan a esos mismos cuatro destinos, así que un `getAllByRole` global devuelve ocho enlaces y
   * la prueba pasaría o fallaría por el motivo equivocado.
   */
  it('monta el carrusel con las cuatro piezas del catálogo', async () => {
    const { container } = await renderPortada();

    const carrusel = container.querySelector('ts-carrusel-hero');
    expect(carrusel).toBeTruthy();
    expect(carrusel!.querySelectorAll('a[href^="/es/productos?linea="]')).toHaveLength(4);
  });

  /**
   * La primera pieza del carrusel es la candidata a LCP, así que es la única con `fetchpriority` —
   * y las cuatro novedades, que lo llevaban cuando la portada no tenía imagen de hero, lo pierden.
   * Priorizar cinco imágenes es no priorizar ninguna.
   */
  it('solo la primera pieza de la banda va priorizada', async () => {
    const { container } = await renderPortada();

    const conPrioridad = container.querySelectorAll('img[fetchpriority="high"]');
    expect(conPrioridad).toHaveLength(1);
    // Por el archivo y no por el `alt`, que es texto y cambia cuando cambia la fotografía: lo que
    // esta prueba afirma es *cuál* de las cuatro piezas va priorizada, no cómo se describe.
    expect(conPrioridad[0].getAttribute('src')).toContain('hero-ropa');
  });

  it('cada línea de negocio lleva al catálogo ya filtrado', async () => {
    await renderPortada();

    expect((await screen.findByRole('link', { name: 'Ropa' })).getAttribute('href')).toBe(
      '/es/productos?linea=ROPA',
    );
    expect(
      (await screen.findByRole('link', { name: 'Calzado deportivo' })).getAttribute('href'),
    ).toBe('/es/productos?linea=CALZADO');
    expect((await screen.findByRole('link', { name: 'Bolsos' })).getAttribute('href')).toBe(
      '/es/productos?linea=BOLSOS',
    );
    expect((await screen.findByRole('link', { name: 'Tecnología' })).getAttribute('href')).toBe(
      '/es/productos?linea=TECNOLOGIA',
    );
  });

  /**
   * Las dos pruebas que había aquí afirmaban lo contrario: que una línea sin categorías cargadas no
   * se ofrecía, y que sin ninguna la sección entera desaparecía. El argumento era bueno —enlazar a
   * una rejilla vacía desde la primera pantalla del sitio se lee como "se agotó"— y aun así la
   * decisión se invirtió el 24 de septiembre de 2026, con el árbol: el menú lateral pinta las
   * cuatro ramas siempre, y una portada que ofrece dos mientras el menú ofrece cuatro se contradice
   * a sí misma en la misma pantalla. Las baldosas son las líneas del negocio, que es un dato del
   * modelo y no de lo que hoy haya cargado.
   */
  it('las cuatro baldosas salen aunque el catálogo sea de pura tecnología', async () => {
    await renderPortada(new RepositorioProductosFalso(), [
      { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
    ]);

    await screen.findByRole('link', { name: 'Tecnología' });

    expect(screen.getByRole('link', { name: 'Ropa' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Calzado deportivo' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Bolsos' })).toBeTruthy();
  });

  it('la sección de líneas se pinta incluso con el catálogo vacío', async () => {
    await renderPortada(new RepositorioProductosFalso(), []);

    expect(screen.getByRole('heading', { name: 'Nuestras líneas' })).toBeTruthy();
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
      // La franja de novedades vive dentro de un `@defer` desde que se hidrata al
      // entrar en pantalla: sin esto `TestBed` no dispara el bloque y no hay
      // tarjetas que consultar.
      deferBlockBehavior: DeferBlockBehavior.Playthrough,
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
      // La franja de novedades vive dentro de un `@defer` desde que se hidrata al
      // entrar en pantalla: sin esto `TestBed` no dispara el bloque y no hay
      // tarjetas que consultar.
      deferBlockBehavior: DeferBlockBehavior.Playthrough,
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
