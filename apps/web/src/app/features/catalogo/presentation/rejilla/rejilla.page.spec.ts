import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Producto } from '../../domain/producto.model';
import { REPOSITORIO_PRODUCTOS, RepositorioProductos } from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { RejillaPage } from './rejilla.page';

function productoDePrueba(slug: string): Producto {
  return {
    slug,
    nombre: `Producto ${slug}`,
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [{ sku: `SKU-${slug}`, precio: { valor: 10_000, moneda: 'COP' }, existencia: 5, atributos: [] }],
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
        provideTanStackQuery(new QueryClient()),
        { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      ],
    });

    expect(await screen.findByText('Producto a')).toBeTruthy();
    expect(screen.getByText('Producto b')).toBeTruthy();
    expect(screen.queryByText('Producto c')).toBeFalsy();

    const boton = await screen.findByRole('button');
    fireEvent.click(boton);

    expect(await screen.findByText('Producto c')).toBeTruthy();
    expect(repositorio.llamadas).toBe(2);
  });
});
