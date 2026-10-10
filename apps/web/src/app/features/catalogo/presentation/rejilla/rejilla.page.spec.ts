import { proveerPaletaDePrueba } from '../../../../../testing/paleta-colores';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Categoria, MarcaDeVitrina, Producto } from '../../domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../domain/repositorio-categorias.puerto';
import {
  REPOSITORIO_MARCAS_DE_VITRINA,
  RepositorioMarcasDeVitrina,
} from '../../domain/repositorio-marcas.puerto';
import {
  REPOSITORIO_PRODUCTOS,
  RepositorioProductos,
} from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { RejillaPage } from './rejilla.page';
import { esperarSinViolaciones } from '../../../../../testing/axe';

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [];
  }
}

class RepositorioMarcasFalso implements RepositorioMarcasDeVitrina {
  async listarDeVitrina(): Promise<MarcaDeVitrina[]> {
    return [];
  }
}

function productoDePrueba(slug: string): Producto {
  return {
    slug,
    nombre: `Producto ${slug}`,
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: {
      id: 'c1',
      nombre: 'Morrales',
      slug: 'bolsos-dama-morrales',
      linea: 'BOLSOS',
      padreId: null,
      hashtags: [],
      escalaTallas: [],
    },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    escalaTallas: [],
    tallaSirveHasta: null,
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

class RepositorioVacioFalso implements RepositorioProductos {
  async buscar(): Promise<ResultadoPaginado<Producto>> {
    return { items: [], cursorSiguiente: null };
  }

  async buscarPorSlug(): Promise<Producto | null> {
    return null;
  }
}

class RepositorioProductosFalso implements RepositorioProductos {
  llamadas = 0;

  async buscar(): Promise<ResultadoPaginado<Producto>> {
    this.llamadas++;
    if (this.llamadas === 1) {
      return { items: [productoDePrueba('a'), productoDePrueba('b')], cursorSiguiente: 'cursor-2' };
    }
    return { items: [productoDePrueba('c')], cursorSiguiente: null };
  }

  async buscarPorSlug(): Promise<Producto | null> {
    return null;
  }
}

function renderRejilla(repositorio: RepositorioProductos, queryParams: Params = {}) {
  return render(RejillaPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      proveerPaletaDePrueba(),
      provideTanStackQuery(new QueryClient()),
      // Los filtros viven en la URL (ADR-0011): para probar el estado vacío
      // con y sin filtros hace falta poder fijarlos, no solo el repositorio.
      {
        provide: ActivatedRoute,
        useValue: { queryParams: of(queryParams), snapshot: { queryParams } },
      },
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
      { provide: REPOSITORIO_MARCAS_DE_VITRINA, useClass: RepositorioMarcasFalso },
    ],
  });
}

describe('RejillaPage', () => {
  it('muestra la primera página y carga la siguiente con "cargar más"', async () => {
    const repositorio = new RepositorioProductosFalso();

    await renderRejilla(repositorio);

    expect(await screen.findByText('Producto a')).toBeTruthy();
    expect(screen.getByText('Producto b')).toBeTruthy();
    expect(screen.queryByText('Producto c')).toBeFalsy();

    const boton = await screen.findByRole('button', { name: /cargar más/i });
    fireEvent.click(boton);

    expect(await screen.findByText('Producto c')).toBeTruthy();
    expect(repositorio.llamadas).toBe(2);
  });

  /**
   * Filtrar y «Cargar más» cambiaban la rejilla sin decir nada. La región `status` vive siempre y
   * dice cuántos hay, y luego cuántos más llegaron; el foco va a la primera tarjeta nueva y el botón
   * no se deshabilita mientras carga (el foco caía en `<body>`).
   */
  it('anuncia cuántos hay y cuántos llegaron, y lleva el foco a la primera tarjeta nueva', async () => {
    await renderRejilla(new RepositorioProductosFalso());
    const region = screen.getByRole('status');

    await vi.waitFor(() => expect(region.textContent?.trim()).toBe('2 productos.'));

    const boton = screen.getByRole('button', { name: /cargar más/i });
    fireEvent.click(boton);

    await vi.waitFor(() => {
      expect(region.textContent?.trim()).toBe('1 producto más.');
      expect(document.activeElement).toBe(screen.getByRole('link', { name: /Producto c/ }));
    });
  });

  it('entre el título de la página y las tarjetas hay un h2: la lista no salta del 1 al 3', async () => {
    await renderRejilla(new RepositorioProductosFalso());
    await screen.findByText('Producto a');

    expect(screen.getByRole('heading', { level: 2, name: 'Productos' })).toBeTruthy();
    expect(screen.getAllByRole('heading', { level: 3 }).length).toBe(2);
  });

  it('sin resultados y con filtros activos, lo dice y sugiere quitar alguno', async () => {
    await renderRejilla(new RepositorioVacioFalso(), { texto: 'zapatilla-que-no-existe' });

    expect(await screen.findByText(/no encontramos productos con estos filtros/i)).toBeTruthy();
    expect(screen.queryByText(/todavía no hay productos publicados/i)).toBeNull();
  });

  it('sin resultados y sin filtros, el catalogo esta vacio: es otro mensaje', async () => {
    await renderRejilla(new RepositorioVacioFalso(), {});

    expect(await screen.findByText(/todavía no hay productos publicados/i)).toBeTruthy();
    expect(screen.queryByText(/no encontramos productos con estos filtros/i)).toBeNull();
  });

  // `docs/06-testing.md`: axe automatizado en las pantallas clave.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderRejilla(new RepositorioProductosFalso());
    await screen.findByText('Producto a');

    await esperarSinViolaciones(container);
  });
});
