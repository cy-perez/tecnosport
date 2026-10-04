import { proveerPaletaDePrueba } from '../../../../../testing/paleta-colores';
import { IMAGE_LOADER } from '@angular/common';
import { cargadorDeImagenes } from '../../../../core/imagenes/cargador-de-imagenes';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { BehaviorSubject, of } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCarrito from '../../../../../assets/i18n/scopes/carrito/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Carrito } from '../../../carrito/domain/carrito.model';
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { Producto } from '../../domain/producto.model';
import {
  REPOSITORIO_PRODUCTOS,
  RepositorioProductos,
} from '../../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../../domain/resultado-paginado.model';
import { FichaPage } from './ficha.page';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { proveerAlmacenesCarrito } from '../../../../../testing/carrito';

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
    categoria: {
      id: 'c1',
      nombre: 'Bolsos',
      slug: 'bolsos',
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
    fotosGeneralesEnCadaColor: true,
    variantes: [
      {
        id: 'variante-1',
        sku: 'SKU-1',
        precio: { valor: 150_000, moneda: 'COP' },
        disponible: true,
        atributos: [],
      },
    ],
  };
}

function productoConVariantes(): Producto {
  return {
    slug: 'camiseta',
    nombre: 'Camiseta running Dry-Fit',
    descripcion: 'Una camiseta transpirable.',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: {
      id: 'c2',
      nombre: 'Ropa deportiva',
      slug: 'ropa-deportiva',
      linea: 'ROPA',
      padreId: null,
      hashtags: [],
      escalaTallas: [],
    },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    escalaTallas: [],
    tallaSirveHasta: null,
    fotosGeneralesEnCadaColor: true,
    variantes: [
      {
        id: 'variante-az',
        sku: 'SKU-AZ',
        precio: { valor: 89_900, moneda: 'COP' },
        disponible: true,
        atributos: [{ nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A', unidad: null }],
      },
      {
        id: 'variante-ng',
        sku: 'SKU-NG',
        precio: { valor: 99_900, moneda: 'COP' },
        disponible: true,
        atributos: [{ nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null }],
      },
    ],
  };
}

function productoConRotacion(): Producto {
  return {
    ...productoDePrueba(),
    imagenPrincipal: {
      url: 'https://imagenes.test/principal.jpg',
      variantes: [{ ancho: 800, url: 'https://imagenes.test/principal.jpg' }],
      urlVistaPrevia: 'https://imagenes.test/principal-previa.jpg',
      ancho: 800,
      alto: 600,
      altEs: 'Morral de frente',
      altEn: 'Backpack, front',
      varianteId: null,
    },
    // Ordenados, como los entrega el mapeador: que el orden se garantice en la frontera es
    // asunto de `mapeador-productos.spec.ts`, no de esta pantalla.
    rotacion: {
      fotogramas: 4,
      imagenes: [0, 1, 2, 3].map((orden) => ({
        orden,
        url: `https://imagenes.test/r${orden}.jpg`,
        ancho: 1000,
        alto: 1000,
      })),
    },
  };
}

/** Espera real: el observador de TanStack propaga a la senal fuera del ciclo de `whenStable`. */

function activatedRouteConSlug(slug: string) {
  const paramMap = convertToParamMap({ slug });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderFicha(repositorio: RepositorioProductos, slug = 'morral-urbano') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resultado = await render(FichaPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo, 'carrito/es': esCarrito } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      { provide: IMAGE_LOADER, useValue: cargadorDeImagenes },
      ...proveerAlmacenesCarrito(),
      proveerPaletaDePrueba(),
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
      { provide: ActivatedRoute, useValue: activatedRouteConSlug(slug) },
    ],
  });
  return { ...resultado, queryClient };
}

/**
 * Como `renderFicha`, pero con el slug de la ruta en un sujeto: permite navegar de una ficha a
 * otra dentro de la misma instancia del componente, que es lo que hace el router de verdad.
 */
async function renderFichaNavegable(repositorio: RepositorioProductos, slugInicial: string) {
  const paramMap = new BehaviorSubject(convertToParamMap({ slug: slugInicial }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const resultado = await render(FichaPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo, 'carrito/es': esCarrito } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      { provide: IMAGE_LOADER, useValue: cargadorDeImagenes },
      ...proveerAlmacenesCarrito(),
      proveerPaletaDePrueba(),
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_PRODUCTOS, useValue: repositorio },
      { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
      {
        provide: ActivatedRoute,
        useValue: { paramMap, snapshot: { paramMap: paramMap.value } },
      },
    ],
  });
  return {
    ...resultado,
    navegarA: (slug: string) => paramMap.next(convertToParamMap({ slug })),
  };
}

describe('FichaPage', () => {
  it('muestra el producto encontrado', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('heading', { name: 'Morral urbano' })).toBeTruthy();
    expect(screen.getByText('TecnoSport')).toBeTruthy();
  });

  it('muestra "no encontrado" cuando el repositorio devuelve null', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(null),
    };

    await renderFicha(repositorio, 'no-existe');

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'No encontramos este producto.',
    );
    // Un enlace roto no puede ser un callejón sin salida.
    expect(screen.getByRole('link', { name: 'Volver al catálogo' })).toBeTruthy();
  });

  it('muestra un mensaje de error si la consulta falla', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
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
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    await renderFicha(repositorio);

    expect(await screen.findByRole('heading', { name: 'Morral urbano' })).toBeTruthy();
    expect(screen.queryByRole('group', { name: 'Vista 360 del producto' })).toBeNull();
  });

  it('un producto con set de rotación muestra el visor, empezando por el fotograma frontal', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConRotacion()),
    };

    await renderFicha(repositorio);

    const visor = await screen.findByRole('group', { name: 'Vista 360 del producto' });
    expect(visor.querySelector('img')?.getAttribute('src')).toContain('r0.jpg');
    expect(screen.getByText('Fotograma 1 de 4')).toBeTruthy();
  });

  // Priorizar las dos imágenes es no priorizar ninguna: con visor, la candidata a LCP es el
  // fotograma frontal y la galería deja de serlo. Con las dos ramas del @if, es lo único que
  // atrapa que alguien cambie una y se olvide de la otra.
  it('con visor, la prioridad de LCP es del fotograma frontal y no de la galería', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConRotacion()),
    };

    await renderFicha(repositorio);

    const visor = await screen.findByRole('group', { name: 'Vista 360 del producto' });
    expect(visor.querySelector('img')?.getAttribute('fetchpriority')).toBe('high');
    expect(
      screen.getByRole('img', { name: 'Morral de frente' }).getAttribute('fetchpriority'),
    ).toBe('auto');
  });

  it('sin visor, la prioridad vuelve a la galería: la pantalla nunca se queda sin candidata', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve({ ...productoConRotacion(), rotacion: null }),
    };

    await renderFicha(repositorio);

    expect(
      (await screen.findByRole('img', { name: 'Morral de frente' })).getAttribute('fetchpriority'),
    ).toBe('high');
  });

  // Un set de un solo fotograma no llega hoy del backend (`PUBLICADO` exige cuatro), pero si
  // llegara, la ficha no puede quedarse con un hueco vacío ni sin candidata a LCP: la galería
  // vuelve a ser la prioritaria.
  it('un set de rotación de un solo fotograma no monta el visor', async () => {
    const producto = productoConRotacion();
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
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

  // TanStack revalida sola al volver a la pestaña pasado el `staleTime`. Que el producto llegue de
  // nuevo del servidor no significa que la rotación haya cambiado: devolver el visor al frontal
  // mientras alguien lo está girando es perder su sitio sin motivo.
  it('un refetch que trae los mismos datos no mueve el visor', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConRotacion()),
    };

    const { fixture, queryClient } = await renderFicha(repositorio);
    const visor = await screen.findByRole('group', { name: 'Vista 360 del producto' });

    fireEvent.keyDown(visor, { key: 'ArrowRight' });
    fireEvent.keyDown(visor, { key: 'ArrowRight' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 3 de 4')).toBeTruthy();

    await queryClient.refetchQueries();
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 3 de 4')).toBeTruthy();
  });

  // Y aunque el producto sí cambie —le subieron el precio mientras miraba—, lo que cambió no es la
  // rotación: el visor no tiene por qué enterarse. El precio nuevo en pantalla es lo que prueba
  // que el refetch llegó de verdad al componente; sin esa comprobación, la prueba pasaría igual
  // aunque la revalidación no hubiera ocurrido.
  it('un refetch que sí trae datos nuevos actualiza el precio pero no mueve el visor', async () => {
    let precio = 150_000;
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => {
        const producto = productoConRotacion();
        return Promise.resolve({
          ...producto,
          variantes: producto.variantes.map((variante) => ({
            ...variante,
            precio: { ...variante.precio, valor: precio },
          })),
        });
      },
    };

    const { fixture, queryClient } = await renderFicha(repositorio);
    const visor = await screen.findByRole('group', { name: 'Vista 360 del producto' });

    fireEvent.keyDown(visor, { key: 'ArrowRight' });
    fireEvent.keyDown(visor, { key: 'ArrowRight' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 3 de 4')).toBeTruthy();
    expect(screen.getByText(/150\.000/)).toBeTruthy();

    precio = 175_000;
    await queryClient.refetchQueries();

    expect(await screen.findByText(/175\.000/)).toBeTruthy();
    expect(screen.getByText('Fotograma 3 de 4')).toBeTruthy();
  });

  // Mismo defecto que el del visor, en el otro control de la ficha: la variante elegida es del
  // visitante, no del último dato que llegó del servidor.
  it('un refetch no devuelve la variante elegida a la de por defecto', async () => {
    let recargo = 0;
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => {
        const producto = productoConVariantes();
        return Promise.resolve({
          ...producto,
          variantes: producto.variantes.map((variante) => ({
            ...variante,
            precio: { ...variante.precio, valor: variante.precio.valor + recargo },
          })),
        });
      },
    };

    const { queryClient } = await renderFicha(repositorio, 'camiseta');

    fireEvent.click(await screen.findByRole('button', { name: 'Negro' }));
    expect(await screen.findByText(/99\.900/)).toBeTruthy();

    recargo = 1_000;
    await queryClient.refetchQueries();

    // 100.900 es el Negro con el recargo. Si la selección se hubiera reiniciado, aquí saldría
    // 90.900: el azul, que es la variante por defecto.
    expect(await screen.findByText(/100\.900/)).toBeTruthy();
  });

  // El otro lado de la misma moneda: no reiniciar en un refetch no puede volverse "no reiniciar
  // nunca". Navegar a otro producto sí tiene que soltar la variante elegida en el anterior.
  it('navegar a otro producto sí reinicia la variante elegida', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: (slug: string) =>
        Promise.resolve(slug === 'camiseta' ? productoConVariantes() : productoDePrueba()),
    };

    const { fixture, navegarA } = await renderFichaNavegable(repositorio, 'camiseta');

    fireEvent.click(await screen.findByRole('button', { name: 'Negro' }));
    expect(await screen.findByText(/99\.900/)).toBeTruthy();

    navegarA('morral-urbano');
    await fixture.whenStable();

    // El morral tiene una sola variante, sin atributos: si la selección de "Negro" hubiera
    // sobrevivido, no encajaría con ninguna y la ficha diría que la combinación no existe.
    expect(await screen.findByText(/150\.000/)).toBeTruthy();
    expect(screen.queryByText('Esta combinación no está disponible.')).toBeNull();
  });

  it('elegir otra variante cambia el precio y la existencia mostrados', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConVariantes()),
    };

    await renderFicha(repositorio, 'camiseta');

    expect(await screen.findByText(/89\.900/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    expect(await screen.findByText(/99\.900/)).toBeTruthy();
    expect(screen.queryByText(/89\.900/)).toBeFalsy();
  });

  /** La talla única no es una elección: se dice, con hasta dónde sirve, y no sale en el selector. */
  it('una prenda de talla única dice su talla en vez de ofrecerla', async () => {
    const bodi: Producto = {
      ...productoDePrueba(),
      slug: 'bodi',
      tallaSirveHasta: 'L',
      escalaTallas: ['XS', 'S', 'M', 'L'],
      variantes: [
        {
          id: 'v-u',
          sku: 'PRV-1',
          precio: { valor: 60_000, moneda: 'COP' },
          disponible: true,
          atributos: [{ nombre: 'Talla', valor: 'Única', colorHex: null, unidad: null }],
        },
      ],
    };
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(bodi),
    };

    await renderFicha(repositorio, 'bodi');

    expect(await screen.findByText(/Talla única/)).toBeTruthy();
    expect(screen.getByText(/Sirve hasta: L/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Única' })).toBeNull();
    expect(screen.queryByRole('button', { name: /^XS/ })).toBeNull();
  });

  /** Como en la tienda de referencia: la escala entera, con lo que no se puede comprar tachado. */
  it('la escala de la categoría enseña todas las tallas y tacha las que no hay', async () => {
    const talla = (valor: string) => [{ nombre: 'Talla', valor, colorHex: null, unidad: null }];
    const camiseta: Producto = {
      ...productoDePrueba(),
      slug: 'camiseta-escala',
      escalaTallas: ['S', 'M', 'L'],
      variantes: [
        {
          id: 'v-m',
          sku: 'SKU-M',
          precio: { valor: 50_000, moneda: 'COP' },
          disponible: true,
          atributos: talla('M'),
        },
        {
          id: 'v-l',
          sku: 'SKU-L',
          precio: { valor: 50_000, moneda: 'COP' },
          disponible: false,
          atributos: talla('L'),
        },
      ],
    };
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(camiseta),
    };

    await renderFicha(repositorio, 'camiseta-escala');

    expect(await screen.findByRole('button', { name: 'M' })).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'S, no disponible' }).getAttribute('aria-disabled'),
    ).toBe('true');
    // L existe pero está agotada: tachada, y se puede elegir para ver que no hay.
    expect(
      screen.getByRole('button', { name: 'L, no disponible' }).getAttribute('aria-disabled'),
    ).toBeNull();
  });

  /**
   * Dos ejes sin color no se bloquean entre sí: con 128/6 y 256/8, elegir 256 salta a 256/8 en vez
   * de quedarse en un 256/6 que no existe.
   */
  it('elegir una opción que no casa con lo elegido salta a la variante que la tiene', async () => {
    const atributos = (almacenamiento: string, ram: string) => [
      { nombre: 'Almacenamiento', valor: almacenamiento, colorHex: null, unidad: null },
      { nombre: 'RAM', valor: ram, colorHex: null, unidad: null },
    ];
    const celular: Producto = {
      ...productoDePrueba(),
      slug: 'celular',
      variantes: [
        {
          id: 'v-128',
          sku: 'CEL-128',
          precio: { valor: 900_000, moneda: 'COP' },
          disponible: true,
          atributos: atributos('128GB', '6GB'),
        },
        {
          id: 'v-256',
          sku: 'CEL-256',
          precio: { valor: 1_100_000, moneda: 'COP' },
          disponible: true,
          atributos: atributos('256GB', '8GB'),
        },
      ],
    };
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(celular),
    };

    await renderFicha(repositorio, 'celular');
    expect(await screen.findByText(/900\.000/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: '256GB, no disponible' }));

    expect(await screen.findByText(/1\.100\.000/)).toBeTruthy();
  });

  // `docs/06-testing.md`: axe automatizado en las pantallas clave. La ficha es
  // la más rica: galería, selector de variantes y precio.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    const { container } = await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Morral urbano' });

    await esperarSinViolaciones(container);
  });
});
