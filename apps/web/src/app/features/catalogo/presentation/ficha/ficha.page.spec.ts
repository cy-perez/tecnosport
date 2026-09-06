import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCarrito from '../../../../../assets/i18n/scopes/carrito/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Carrito } from '../../../carrito/domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../../../carrito/domain/repositorio-carrito.puerto';
import { Producto } from '../../domain/producto.model';
import { REPOSITORIO_PRODUCTOS, RepositorioProductos } from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { FichaPage } from './ficha.page';

class RepositorioCarritoFalso implements RepositorioCarrito {
  crear(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  ver(): Promise<Carrito | null> {
    return Promise.resolve(null);
  }
  agregarLinea(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  actualizarCantidad(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  eliminarLinea(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
}

function productoDePrueba(): Producto {
  return {
    slug: 'morral-urbano',
    nombre: 'Morral urbano',
    descripcion: 'Un morral resistente para el día a día.',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [
      { id: 'variante-1', sku: 'SKU-1', precio: { valor: 150_000, moneda: 'COP' }, existencia: 3, atributos: [] },
    ],
  };
}

function productoConVariantes(): Producto {
  return {
    slug: 'camiseta',
    nombre: 'Camiseta running Dry-Fit',
    descripcion: 'Una camiseta transpirable.',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { id: 'c2', nombre: 'Ropa deportiva', slug: 'ropa-deportiva', linea: 'ROPA_Y_CALZADO' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [
      {
        id: 'variante-az',
        sku: 'SKU-AZ',
        precio: { valor: 89_900, moneda: 'COP' },
        existencia: 5,
        atributos: [{ nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A' }],
      },
      {
        id: 'variante-ng',
        sku: 'SKU-NG',
        precio: { valor: 99_900, moneda: 'COP' },
        existencia: 3,
        atributos: [{ nombre: 'Color', valor: 'Negro', colorHex: '#111111' }],
      },
    ],
  };
}

/** Los fotogramas llegan desordenados a propósito: el orden del giro lo decide `orden`, no el arreglo. */
function productoConRotacion(): Producto {
  return {
    ...productoDePrueba(),
    rotacion: {
      fotogramas: 4,
      imagenes: [
        { orden: 2, url: 'https://imagenes.test/r2.jpg', urlWebp: 'https://imagenes.test/r2.webp', ancho: 1000, alto: 1000 },
        { orden: 0, url: 'https://imagenes.test/r0.jpg', urlWebp: 'https://imagenes.test/r0.webp', ancho: 1000, alto: 1000 },
        { orden: 3, url: 'https://imagenes.test/r3.jpg', urlWebp: 'https://imagenes.test/r3.webp', ancho: 1000, alto: 1000 },
        { orden: 1, url: 'https://imagenes.test/r1.jpg', urlWebp: 'https://imagenes.test/r1.webp', ancho: 1000, alto: 1000 },
      ],
    },
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
        langs: { es, en, 'catalogo/es': esCatalogo, 'carrito/es': esCarrito } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
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

  it('un producto sin set de rotación no muestra el visor 360', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('heading', { name: 'Morral urbano' })).toBeTruthy();
    expect(screen.queryByRole('group', { name: 'Vista 360 del producto' })).toBeNull();
  });

  it('un producto con set de rotación muestra el visor, empezando por el fotograma frontal', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConRotacion()),
    };

    await renderFicha(repositorio);

    const visor = await screen.findByRole('group', { name: 'Vista 360 del producto' });
    // El frontal es el de `orden: 0`, aunque llegue en segundo lugar en el arreglo.
    expect(visor.querySelector('img')?.getAttribute('src')).toContain('r0.webp');
    expect(screen.getByText('Fotograma 1 de 4')).toBeTruthy();
  });

  // Un set de un solo fotograma no llega hoy del backend (`PUBLICADO` exige cuatro), pero si
  // llegara, la ficha no puede quedarse con un hueco vacío ni sin candidata a LCP: la galería
  // vuelve a ser la prioritaria.
  it('un set de rotación de un solo fotograma no monta el visor', async () => {
    const producto = productoConRotacion();
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () =>
        Promise.resolve({
          ...producto,
          rotacion: { fotogramas: 1, imagenes: [producto.rotacion!.imagenes[0]] },
        }),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('heading', { name: 'Morral urbano' })).toBeTruthy();
    expect(screen.queryByRole('group', { name: 'Vista 360 del producto' })).toBeNull();
  });

  it('elegir otra variante cambia el precio y la existencia mostrados', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () => Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConVariantes()),
    };

    await renderFicha(repositorio, 'camiseta');

    expect(await screen.findByText(/89\.900/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    expect(await screen.findByText(/99\.900/)).toBeTruthy();
    expect(screen.queryByText(/89\.900/)).toBeFalsy();
  });
});
