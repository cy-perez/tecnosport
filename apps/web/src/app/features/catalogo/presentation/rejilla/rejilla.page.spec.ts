import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Categoria, Marca, Producto } from '../../domain/producto.model';
import { REPOSITORIO_CATEGORIAS, RepositorioCategorias } from '../../domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../../domain/repositorio-marcas.puerto';
import { REPOSITORIO_PRODUCTOS, RepositorioProductos } from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { RejillaPage } from './rejilla.page';

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [];
  }
}

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [];
  }
}

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
      { id: `id-${slug}`, sku: `SKU-${slug}`, precio: { valor: 10_000, moneda: 'COP' }, existencia: 5, atributos: [] },
    ],
  };
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

describe('RejillaPage', () => {
  it('muestra la primera página y carga la siguiente con "cargar más"', async () => {
    const repositorio = new RepositorioProductosFalso();

    await render(RejillaPage, {
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
        { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
        { provide: REPOSITORIO_MARCAS, useClass: RepositorioMarcasFalso },
      ],
    });

    expect(await screen.findByText('Producto a')).toBeTruthy();
    expect(screen.getByText('Producto b')).toBeTruthy();
    expect(screen.queryByText('Producto c')).toBeFalsy();

    const boton = await screen.findByRole('button', { name: /cargar más/i });
    fireEvent.click(boton);

    expect(await screen.findByText('Producto c')).toBeTruthy();
    expect(repositorio.llamadas).toBe(2);
  });
});
