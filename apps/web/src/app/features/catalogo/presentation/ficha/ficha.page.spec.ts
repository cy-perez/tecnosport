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
import { Carrito, CarritoCotizado } from '../../../carrito/domain/carrito.model';
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
import { EnlaceDeCompra } from '../../domain/enlace-de-compra.model';
import {
  REPOSITORIO_ENLACES_DE_COMPRA,
  RepositorioEnlacesDeCompra,
} from '../../domain/repositorio-enlaces-de-compra.puerto';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { proveerAlmacenesCarrito } from '../../../../../testing/carrito';

/** El precio que el servidor da hoy en estas pruebas. */
const PRECIO_DE_HOY = 150_000;

class RepositorioCarritoFalso implements RepositorioCarrito {
  /** Los precios de hoy, como el servidor: {@link PRECIO_DE_HOY} por unidad. */
  async cotizar(carritoId: string): Promise<CarritoCotizado | null> {
    const carrito = await this.ver();
    void carritoId;
    if (!carrito) {
      return null;
    }
    const lineas = carrito.lineas.map((linea) => ({
      lineaId: linea.id,
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
      precioUnitario: PRECIO_DE_HOY,
      subtotal: PRECIO_DE_HOY * linea.cantidad,
      precioContado: PRECIO_DE_HOY,
      subtotalContado: PRECIO_DE_HOY * linea.cantidad,
    }));
    return {
      lineas,
      subtotal: lineas.reduce((suma, linea) => suma + linea.subtotal, 0),
      subtotalContado: lineas.reduce((suma, linea) => suma + linea.subtotalContado, 0),
    };
  }
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

/** Un carrito que sí acepta la línea: el camino feliz de "Agregar al carrito". */
class RepositorioCarritoQueAgrega extends RepositorioCarritoFalso {
  override crear(): Promise<Carrito> {
    return Promise.resolve({ id: 'c1', usuarioId: null, lineas: [], creadoEn: '2026-10-04' });
  }
  override agregarLinea(): Promise<Carrito> {
    return Promise.resolve({
      id: 'c1',
      usuarioId: null,
      lineas: [{ id: 'l1', varianteId: 'variante-1', cantidad: 1 }],
      creadoEn: '2026-10-04',
    });
  }
}

/** El que rechaza la línea por el tope de unidades: la variante ya tiene el máximo en el carrito. */
class RepositorioCarritoEnElMaximo extends RepositorioCarritoQueAgrega {
  override agregarLinea(): Promise<Carrito> {
    return Promise.reject(
      new ErrorHttp(422, 'tope de unidades por línea', 'CANTIDAD_POR_LINEA_EXCEDIDA'),
    );
  }
}

/** El que acepta la línea y anota qué variante le llegó: qué se compró, no solo que se compró. */
const variantesAgregadas: string[] = [];
/** Y con qué enlace de compra llegó cada una: `null` si llegó sin ninguno. */
const enlacesAgregados: (string | null)[] = [];
class RepositorioCarritoQueAnota extends RepositorioCarritoQueAgrega {
  // Con resto y no con la firma del puerto: la base la declara sin parámetros, y una sobrescritura
  // que los exija no le es asignable.
  override agregarLinea(...argumentos: unknown[]): Promise<Carrito> {
    variantesAgregadas.push(argumentos[1] as string);
    enlacesAgregados.push((argumentos[3] as string | null | undefined) ?? null);
    return super.agregarLinea();
  }
}

/** Los enlaces de compra que la API reconoce en estas pruebas; cualquier otro ya no sirve. */
const enlacesVigentes = new Map<string, EnlaceDeCompra>();
const repositorioEnlaces: RepositorioEnlacesDeCompra = {
  consultar: (token) => Promise.resolve(enlacesVigentes.get(token) ?? null),
};

function productoDePrueba(): Producto {
  return {
    slug: 'morral-urbano',
    nombre: 'Morral urbano',
    descripcion: 'Un morral resistente para el día a día.',
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
    escalaTallas: [],
    tallaSirveHasta: null,
    paleta: [],
    variantes: [
      {
        id: 'variante-1',
        sku: 'SKU-1',
        precio: { valor: 150_000, moneda: 'COP' },
        precioContado: { valor: 150_000, moneda: 'COP' },
        disponible: true,
        disponibilidadPorConfirmar: false,
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
      nombre: 'Camisetas',
      slug: 'ropa-caballero-camisetas',
      linea: 'ROPA',
      padreId: null,
      hashtags: [],
      escalaTallas: [],
    },
    imagenPrincipal: null,
    galeria: [],
    escalaTallas: [],
    tallaSirveHasta: null,
    paleta: [],
    variantes: [
      {
        id: 'variante-az',
        sku: 'SKU-AZ',
        precio: { valor: 89_900, moneda: 'COP' },
        precioContado: { valor: 89_900, moneda: 'COP' },
        disponible: true,
        disponibilidadPorConfirmar: false,
        atributos: [{ nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A', unidad: null }],
      },
      {
        id: 'variante-ng',
        sku: 'SKU-NG',
        precio: { valor: 99_900, moneda: 'COP' },
        precioContado: { valor: 99_900, moneda: 'COP' },
        disponible: true,
        disponibilidadPorConfirmar: false,
        atributos: [{ nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null }],
      },
    ],
  };
}

/**
 * Una foto publicada, con lo justo para la galería.
 */
function foto(url: string, varianteId: string | null) {
  return {
    url,
    variantes: [{ ancho: 800, url }],
    urlVistaPrevia: null,
    ancho: 800,
    alto: 800,
    altEs: url,
    altEn: url,
    varianteId,
    enLaFicha: false,
    variantesQueMuestra: [],
  };
}

/**
 * Una principal que «vale para todos los tonos» —sin color— y una foto por color. Es como sale de
 * la revisión cuando la portada es una toma de catálogo y cada color trae la suya.
 */
function productoConPrincipalGenerica(): Producto {
  return {
    ...productoConVariantes(),
    imagenPrincipal: foto('https://imagenes.test/principal.jpg', null),
    galeria: [
      foto('https://imagenes.test/azul.jpg', 'variante-az'),
      foto('https://imagenes.test/negro.jpg', 'variante-ng'),
    ],
  };
}

/**
 * Dos fotos del negro y una general sin color: para ver que la galería se queda en la foto que se
 * tocó —no salta a la primera del color— y que una foto sin color no cambia lo que se compra.
 */
function productoConVariasFotosPorColor(): Producto {
  return {
    ...productoConPrincipalGenerica(),
    galeria: [
      foto('https://imagenes.test/azul.jpg', 'variante-az'),
      foto('https://imagenes.test/negro.jpg', 'variante-ng'),
      foto('https://imagenes.test/negro-espalda.jpg', 'variante-ng'),
      foto('https://imagenes.test/detalle.jpg', null),
    ],
  };
}

/** Y la misma, con la principal retratando el azul: entonces sí encabeza la ficha. */
function productoConPrincipalDeUnColor(): Producto {
  return {
    ...productoConPrincipalGenerica(),
    imagenPrincipal: foto('https://imagenes.test/principal.jpg', 'variante-az'),
  };
}

function productoConFotoPrincipal(): Producto {
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
      enLaFicha: false,
      variantesQueMuestra: [],
    },
  };
}

/** Espera real: el observador de TanStack propaga a la senal fuera del ciclo de `whenStable`. */

function activatedRouteConSlug(slug: string, enlace: string | null = null) {
  const paramMap = convertToParamMap({ slug });
  const queryParamMap = convertToParamMap(enlace ? { enlace } : {});
  return {
    paramMap: of(paramMap),
    queryParamMap: of(queryParamMap),
    snapshot: { paramMap, queryParamMap },
  };
}

async function renderFicha(
  repositorio: RepositorioProductos,
  slug = 'morral-urbano',
  repositorioCarrito: new () => RepositorioCarrito = RepositorioCarritoFalso,
  enlace: string | null = null,
) {
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
      { provide: REPOSITORIO_CARRITO, useClass: repositorioCarrito },
      { provide: REPOSITORIO_ENLACES_DE_COMPRA, useValue: repositorioEnlaces },
      { provide: ActivatedRoute, useValue: activatedRouteConSlug(slug, enlace) },
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
      { provide: REPOSITORIO_ENLACES_DE_COMPRA, useValue: repositorioEnlaces },
      {
        provide: ActivatedRoute,
        useValue: {
          paramMap,
          queryParamMap: of(convertToParamMap({})),
          snapshot: { paramMap: paramMap.value, queryParamMap: convertToParamMap({}) },
        },
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
    // «Marca:» en negrita y luego el nombre, como en la tarjeta.
    const etiqueta = screen.getByText('Marca:');
    expect(etiqueta.tagName).toBe('STRONG');
    expect(etiqueta.parentElement!.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'Marca: TecnoSport',
    );
  });

  /**
   * La ficha pintaba la descripción en un solo `<p>`: el cliente leía «## Ficha técnica |
   * Atributo | Detalle |---|---|» en un bloque corrido. Las descripciones de tecnología llegan con
   * secciones, y la ficha tiene que enseñarlas como secciones.
   */
  describe('la descripción por secciones', () => {
    const DESCRIPCION = [
      'Parlante inteligente con Alexa.',
      '',
      '## Ficha técnica',
      '',
      '| Atributo | Detalle |',
      '|---|---|',
      '| Sonido | altavoz frontal de 44 mm |',
      '',
      '---',
      '',
      'Ficha técnica: Datos de Open Icecat (https://icecat.biz).',
    ].join('\n');
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve({ ...productoDePrueba(), descripcion: DESCRIPCION }),
    };

    it('enseña subtítulos y la ficha técnica en tabla, sin la sintaxis ni las fuentes', async () => {
      const { container } = await renderFicha(repositorio);

      expect(await screen.findByRole('heading', { level: 2, name: 'Ficha técnica' })).toBeTruthy();
      expect(screen.getByRole('rowheader', { name: 'Sonido' })).toBeTruthy();
      // Decisión del negocio del 23/10/2026: la ficha no dice de dónde salió cada dato.
      expect(container.textContent).not.toContain('Icecat');
      expect(container.textContent).not.toContain('##');
      expect(container.textContent).not.toContain('|---');
    });

    it('no tiene violaciones de WCAG 2.2 AA', async () => {
      const { container } = await renderFicha(repositorio);
      await screen.findByRole('heading', { level: 2, name: 'Ficha técnica' });

      await esperarSinViolaciones(container);
    });
  });

  describe('las fotos de referencia', () => {
    const repositorioCon = (producto: Producto): RepositorioProductos => ({
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(producto),
    });

    it('en tecnología la ficha dice que las fotos son de referencia', async () => {
      const base = productoDePrueba();
      await renderFicha(
        repositorioCon({
          ...base,
          nombre: 'Samsung Galaxy A17 5G',
          categoria: { ...base.categoria, linea: 'TECNOLOGIA' },
        }),
      );

      await screen.findByRole('heading', { name: 'Samsung Galaxy A17 5G' });
      expect(screen.getByText(esCatalogo.ficha.imagen_de_referencia)).toBeTruthy();
    });

    it('en las demás líneas no lo dice', async () => {
      await renderFicha(repositorioCon(productoDePrueba()));

      await screen.findByRole('heading', { name: 'Morral urbano' });
      expect(screen.queryByText(esCatalogo.ficha.imagen_de_referencia)).toBeNull();
    });
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

  /**
   * <b>El defecto del 7 de octubre de 2026.</b> La galería enseñaba solo las fotos del color
   * elegido y, con la casilla de "las generales acompañan a cada color" apagada, ni siquiera la
   * principal. Un pantalón con nueve fotos y una por color abría con <b>una</b> foto y sin tira
   * de miniaturas: nada decía que hubiera más, y a la principal no se llegaba con ningún color.
   */
  it('la galería enseña las fotos de color, sin la principal genérica', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConPrincipalGenerica()),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    // Las dos de color. Antes salía una sola y sin tira; la principal genérica se queda fuera:
    // es la portada del catálogo y no retrata ninguna de las prendas que se pueden elegir.
    expect(screen.getAllByRole('button', { name: /imagenes[.]test/ })).toHaveLength(2);
    expect(screen.queryByRole('img', { name: 'https://imagenes.test/principal.jpg' })).toBeNull();
  });

  /**
   * Y si no hay galería, la principal entra aunque sea genérica: una ficha sin una sola foto no le
   * sirve a nadie. Es el mismo respaldo que tenía el recorte por color, y el caso existe —un
   * producto cargado a mano con su portada y nada más.
   */
  it('sin galería, la principal genérica entra igual', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve({ ...productoConPrincipalGenerica(), galeria: [] }),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    const grande = screen.getByRole('img', { name: 'https://imagenes.test/principal.jpg' });
    expect(grande.getAttribute('src')).toContain('principal.jpg');
  });

  /**
   * Y con color sí encabeza: entonces es la foto de ese tono, y además es la que quien viene de
   * la rejilla acaba de ver en la tarjeta.
   */
  it('una principal con color encabeza la ficha', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConPrincipalDeUnColor()),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    expect(screen.getAllByRole('button', { name: /imagenes[.]test/ })).toHaveLength(3);
    const grande = screen.getByRole('img', { name: 'https://imagenes.test/principal.jpg' });
    expect(grande.getAttribute('src')).toContain('principal.jpg');
  });

  /**
   * La blusa de Violeta (ADR-0082): todas las fotos retratan los tres tonos, y quien aprobó pidió
   * que la principal entrara. Sin eso, la foto que eligió de principal no se veía en la ficha.
   */
  it('una principal genérica que pidió entrar encabeza la ficha', async () => {
    const generica = productoConPrincipalGenerica();
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () =>
        Promise.resolve({
          ...generica,
          imagenPrincipal: { ...generica.imagenPrincipal!, enLaFicha: true },
        }),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    expect(screen.getAllByRole('button', { name: /imagenes[.]test/ })).toHaveLength(3);
    const grande = screen.getByRole('img', { name: 'https://imagenes.test/principal.jpg' });
    expect(grande.getAttribute('src')).toContain('principal.jpg');
  });

  /**
   * La camiseta de cinco tonos en dos fotos: el negro no tiene foto propia y quien aprobó dijo que
   * se ve en la segunda. Elegirlo lleva a ella.
   */
  it('elegir un color sin foto propia salta a la foto que lo retrata', async () => {
    const generica = productoConPrincipalGenerica();
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () =>
        Promise.resolve({
          ...generica,
          galeria: [
            foto('https://imagenes.test/azul.jpg', 'variante-az'),
            {
              ...foto('https://imagenes.test/varios-tonos.jpg', null),
              variantesQueMuestra: ['variante-ng'],
            },
          ],
        }),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    await vi.waitFor(() => {
      const grande = document.querySelector('.aspect-square img') as HTMLImageElement;
      expect(grande.getAttribute('src')).toContain('varios-tonos.jpg');
    });
  });

  /** Y elegir un color mueve la foto activa; no recorta la lista. */
  it('elegir un color salta a su foto sin esconder las demás', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConPrincipalGenerica()),
    };

    await renderFicha(repositorio);
    await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    await vi.waitFor(() => {
      const grande = document.querySelector('.aspect-square img') as HTMLImageElement;
      expect(grande.getAttribute('src')).toContain('negro.jpg');
    });
    // Las dos siguen en la tira: lo que cambió es cuál está activa, no cuántas hay.
    expect(screen.getAllByRole('button', { name: /imagenes\.test/ })).toHaveLength(2);
  });

  // La galería es la única candidata a LCP de la ficha y lo declara siempre: sin ella la pantalla se
  // quedaría sin imagen prioritaria.
  it('la foto principal de la galería es la prioritaria de la pantalla', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConFotoPrincipal()),
    };

    await renderFicha(repositorio);

    expect(
      (await screen.findByRole('img', { name: 'Morral de frente' })).getAttribute('fetchpriority'),
    ).toBe('high');
  });

  // TanStack revalida sola al volver a la pestaña pasado el `staleTime`: la variante elegida es del
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

  /**
   * ADR-0095: debajo del precio de la variante elegida, el ahorro de pagar sin pasarela — nunca el
   * contado como un segundo precio— y el de la variante que se elija.
   */
  it('dice el ahorro de la variante elegida y cambia con ella', async () => {
    const base = productoConVariantes();
    const producto: Producto = {
      ...base,
      variantes: [
        { ...base.variantes[0], precioContado: { valor: 85_000, moneda: 'COP' } },
        { ...base.variantes[1], precioContado: { valor: 94_000, moneda: 'COP' } },
      ],
    };
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(producto),
    };

    await renderFicha(repositorio, 'camiseta');

    expect(
      await screen.findByText(
        /^Ahorras \$\s?4\.900 pagando por transferencia, en el punto o contra entrega$/,
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/85\.000/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    expect(await screen.findByText(/^Ahorras \$\s?5\.900/)).toBeTruthy();
  });

  it('sin descuento no dice ningún ahorro', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };

    await renderFicha(repositorio);

    await screen.findByRole('heading', { name: 'Morral urbano' });
    expect(screen.queryByText(/Ahorras/)).toBeNull();
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
      paleta: [],
      escalaTallas: ['XS', 'S', 'M', 'L'],
      variantes: [
        {
          id: 'v-u',
          sku: 'PRV-1',
          precio: { valor: 60_000, moneda: 'COP' },
          precioContado: { valor: 60_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
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

  /**
   * Solo lo que se puede elegir. La escala entera con lo que no se podía comprar tachado llenaba la
   * ficha de la falda short —tallas 8 a 16 en una categoría XS–XXXL— de botones que no servían.
   */
  it('enseña solo las tallas que se pueden comprar', async () => {
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
          precioContado: { valor: 50_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
          atributos: talla('M'),
        },
        {
          id: 'v-l',
          sku: 'SKU-L',
          precio: { valor: 50_000, moneda: 'COP' },
          precioContado: { valor: 50_000, moneda: 'COP' },
          disponible: false,
          disponibilidadPorConfirmar: false,
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
    // S es de la escala y el producto no la trae; L la trae, pero agotada.
    expect(screen.queryByRole('button', { name: /^S(,|$)/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^L(,|$)/ })).toBeNull();
  });

  /** Las tallas son las del color elegido: al cambiar de color, cambian. */
  it('al cambiar de color enseña las tallas que hay en ese color', async () => {
    const atributos = (color: string, hex: string, talla: string) => [
      { nombre: 'Color', valor: color, colorHex: hex, unidad: null },
      { nombre: 'Talla', valor: talla, colorHex: null, unidad: null },
    ];
    const variante = (id: string, disponible: boolean, color: string, hex: string, t: string) => ({
      id,
      sku: id,
      precio: { valor: 50_000, moneda: 'COP' as const },
      precioContado: { valor: 50_000, moneda: 'COP' as const },
      disponible,
      atributos: atributos(color, hex, t),
      disponibilidadPorConfirmar: false,
    });
    const camiseta: Producto = {
      ...productoDePrueba(),
      slug: 'camiseta-colores',
      escalaTallas: ['S', 'M', 'L'],
      variantes: [
        variante('negro-s', true, 'Negro', '#111111', 'S'),
        variante('negro-m', false, 'Negro', '#111111', 'M'),
        variante('vino-m', true, 'Vino', '#722F37', 'M'),
        variante('vino-l', true, 'Vino', '#722F37', 'L'),
      ],
    };
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(camiseta),
    };

    await renderFicha(repositorio, 'camiseta-colores');

    expect(await screen.findByRole('button', { name: 'S' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^M(,|$)/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^L(,|$)/ })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Vino/ }));

    expect(await screen.findByRole('button', { name: /^M(,|$)/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^L(,|$)/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^S(,|$)/ })).toBeNull();
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
          precioContado: { valor: 900_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
          atributos: atributos('128GB', '6GB'),
        },
        {
          id: 'v-256',
          sku: 'CEL-256',
          precio: { valor: 1_100_000, moneda: 'COP' },
          precioContado: { valor: 1_100_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
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

  /**
   * Agregar al carrito no decía nada: ni "listo" ni "falló". La región `status` vive siempre en el
   * DOM —NVDA calla si nace llena— y lo que cambia es el texto. Y el botón no se deshabilita
   * mientras agrega: deshabilitado, el foco se iba a `<body>`.
   */
  describe('tocar una miniatura', () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoConVariasFotosPorColor()),
    };
    const fotoGrande = () =>
      (document.querySelector('.aspect-square img') as HTMLImageElement).getAttribute('src');
    const pulsado = (nombre: string) =>
      screen.getByRole('button', { name: nombre }).getAttribute('aria-pressed');

    beforeEach(() => {
      variantesAgregadas.length = 0;
    });

    /**
     * El defecto: la miniatura del azul cielo se veía y lo que se agregaba era el beige de la
     * variante por defecto, porque solo el círculo de color cambiaba lo elegido.
     */
    it('elige el color de la foto, y es ese el que se agrega al carrito', async () => {
      await renderFicha(repositorio, 'camiseta', RepositorioCarritoQueAnota);
      await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });
      expect(pulsado('Azul marino')).toBe('true');

      fireEvent.click(screen.getByRole('button', { name: 'https://imagenes.test/negro.jpg' }));
      await vi.waitFor(() => expect(pulsado('Negro')).toBe('true'));
      expect(pulsado('Azul marino')).toBe('false');

      fireEvent.click(screen.getByRole('button', { name: 'Agregar al carrito' }));
      await vi.waitFor(() => expect(variantesAgregadas).toEqual(['variante-ng']));
    });

    /**
     * Con un círculo ya pulsado la galería sigue al color elegido, y elegir el negro desde su
     * segunda foto la devolvía a la primera: la foto tocada se escapaba bajo el dedo.
     */
    it('se queda en la foto que se tocó, aunque no sea la primera de su color', async () => {
      await renderFicha(repositorio);
      await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });
      fireEvent.click(screen.getByRole('button', { name: 'Azul marino' }));
      await vi.waitFor(() => expect(fotoGrande()).toContain('azul.jpg'));

      fireEvent.click(
        screen.getByRole('button', { name: 'https://imagenes.test/negro-espalda.jpg' }),
      );

      await vi.waitFor(() => expect(pulsado('Negro')).toBe('true'));
      expect(fotoGrande()).toContain('negro-espalda.jpg');
    });

    it('una foto sin color no cambia lo que se compra', async () => {
      await renderFicha(repositorio);
      await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });
      fireEvent.click(screen.getByRole('button', { name: 'Negro' }));
      await vi.waitFor(() => expect(pulsado('Negro')).toBe('true'));

      fireEvent.click(screen.getByRole('button', { name: 'https://imagenes.test/detalle.jpg' }));

      await vi.waitFor(() => expect(fotoGrande()).toContain('detalle.jpg'));
      expect(pulsado('Negro')).toBe('true');
    });

    it('un círculo de color después vuelve a llevar a la primera foto de su color', async () => {
      await renderFicha(repositorio);
      await screen.findByRole('heading', { name: 'Camiseta running Dry-Fit' });
      fireEvent.click(
        screen.getByRole('button', { name: 'https://imagenes.test/negro-espalda.jpg' }),
      );
      await vi.waitFor(() => expect(fotoGrande()).toContain('negro-espalda.jpg'));

      fireEvent.click(screen.getByRole('button', { name: 'Azul marino' }));
      await vi.waitFor(() => expect(fotoGrande()).toContain('azul.jpg'));

      fireEvent.click(screen.getByRole('button', { name: 'Negro' }));
      await vi.waitFor(() => expect(fotoGrande()).toMatch(/\/negro\.jpg/));
    });
  });

  it('anuncia lo que se agregó al carrito, sin sacar el foco del botón', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };
    await renderFicha(repositorio, 'morral-urbano', RepositorioCarritoQueAgrega);
    const boton = await screen.findByRole('button', { name: 'Agregar al carrito' });
    const region = screen.getByRole('status');
    expect(region.textContent?.trim()).toBe('');

    fireEvent.click(boton);

    await vi.waitFor(() =>
      expect(region.textContent?.trim()).toBe('Agregado al carrito: Morral urbano'),
    );
    expect((boton as HTMLButtonElement).disabled).toBe(false);
  });

  it('dice que no se pudo agregar cuando el carrito rechaza, en vez de tragárselo', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };
    await renderFicha(repositorio);
    const boton = await screen.findByRole('button', { name: 'Agregar al carrito' });

    fireEvent.click(boton);

    await vi.waitFor(() =>
      expect(screen.getByRole('status').textContent).toMatch(/no pudimos agregarlo/i),
    );
  });

  /** El tope no es un fallo de conexión: decirle "revisa tu conexión" la manda a reintentar. */
  it('dice que ya tiene el máximo cuando el carrito rechaza por el tope de unidades', async () => {
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(productoDePrueba()),
    };
    await renderFicha(repositorio, 'morral-urbano', RepositorioCarritoEnElMaximo);
    const boton = await screen.findByRole('button', { name: 'Agregar al carrito' });

    fireEvent.click(boton);

    await vi.waitFor(() =>
      expect(screen.getByRole('status').textContent?.trim()).toBe(
        'Ya tienes en el carrito el máximo de unidades de este producto.',
      ),
    );
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

  describe('el color de tecnología por confirmar (ADR-0086)', () => {
    const color = (valor: string, hex: string) => [
      { nombre: 'Color', valor, colorHex: hex, unidad: null },
      { nombre: 'Almacenamiento', valor: '256', colorHex: null, unidad: 'GB' },
    ];
    const iphone = (): Producto => ({
      ...productoDePrueba(),
      slug: 'iphone-18-pro',
      nombre: 'Apple iPhone 18 Pro',
      categoria: { ...productoDePrueba().categoria, nombre: 'Celulares', linea: 'TECNOLOGIA' },
      paleta: ['Negro', 'Morado', 'Plata'],
      variantes: [
        {
          id: 'v-negro',
          sku: 'IPH-NE',
          precio: { valor: 6_000_000, moneda: 'COP' },
          precioContado: { valor: 6_000_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
          atributos: color('Negro', '#111111'),
        },
        {
          id: 'v-morado',
          sku: 'IPH-MO',
          precio: { valor: 6_000_000, moneda: 'COP' },
          precioContado: { valor: 6_000_000, moneda: 'COP' },
          disponible: false,
          disponibilidadPorConfirmar: true,
          atributos: color('Morado', '#6B3FA0'),
        },
      ],
    });
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(iphone()),
    };

    beforeEach(() => {
      variantesAgregadas.length = 0;
      enlacesAgregados.length = 0;
      enlacesVigentes.clear();
    });

    it('dice al pie de las fotos los colores del fabricante y que son de referencia', async () => {
      await renderFicha(repositorio, 'iphone-18-pro');

      expect(
        await screen.findByText('Colores del modelo según el fabricante: Negro, Morado y Plata.'),
      ).toBeTruthy();
      expect(
        screen.getByText('Las imágenes son de referencia: muestran el modelo, no cada color.'),
      ).toBeTruthy();
    });

    it('un color sin confirmar no se agrega: se pregunta por WhatsApp con el mensaje', async () => {
      await renderFicha(repositorio, 'iphone-18-pro');
      await screen.findByRole('heading', { name: 'Apple iPhone 18 Pro' });

      fireEvent.click(screen.getByRole('button', { name: 'Morado' }));

      const chat = await screen.findByRole('link', {
        name: 'Consultar disponibilidad por WhatsApp (se abre en otra pestaña)',
      });
      const href = chat.getAttribute('href') ?? '';
      expect(href.startsWith('https://wa.me/')).toBe(true);
      const mensaje = decodeURIComponent(href.split('?text=')[1]);
      expect(mensaje).toContain('Apple iPhone 18 Pro (256 GB · Morado)');
      expect(mensaje).toContain('/es/productos/iphone-18-pro');
      expect(screen.queryByRole('button', { name: 'Agregar al carrito' })).toBeNull();
    });

    it('el color que la lista confirmó se agrega como siempre', async () => {
      await renderFicha(repositorio, 'iphone-18-pro', RepositorioCarritoQueAnota);
      await screen.findByRole('heading', { name: 'Apple iPhone 18 Pro' });

      fireEvent.click(screen.getByRole('button', { name: 'Agregar al carrito' }));

      await vi.waitFor(() => expect(variantesAgregadas).toEqual(['v-negro']));
      expect(enlacesAgregados).toEqual([null]);
    });

    it('con su enlace vigente abre en ese color y lo agrega con el enlace', async () => {
      enlacesVigentes.set('token-1', {
        token: 'token-1',
        varianteId: 'v-morado',
        expiraEn: '2026-10-25T20:00:00Z',
      });
      await renderFicha(repositorio, 'iphone-18-pro', RepositorioCarritoQueAnota, 'token-1');

      await screen.findByText(/^Color confirmado\. Tu unidad está apartada hasta el/);
      expect(screen.getByRole('button', { name: 'Morado' }).getAttribute('aria-pressed')).toBe(
        'true',
      );
      fireEvent.click(screen.getByRole('button', { name: 'Agregar al carrito' }));

      await vi.waitFor(() => expect(variantesAgregadas).toEqual(['v-morado']));
      expect(enlacesAgregados).toEqual(['token-1']);
    });

    it('un enlace que ya no sirve se dice', async () => {
      await renderFicha(repositorio, 'iphone-18-pro', RepositorioCarritoFalso, 'vencido');

      expect(
        await screen.findByText(
          'Este enlace de compra ya no está vigente. Escríbenos por WhatsApp y te confirmamos el color de nuevo.',
        ),
      ).toBeTruthy();
    });
  });

  describe('la talla de un tenis por confirmar (ADR-0089)', () => {
    const tenis = (): Producto => ({
      ...productoDePrueba(),
      slug: 'tenis-importados-estilo-nike-air-force-1',
      nombre: 'Tenis importados estilo Nike Air Force 1',
      categoria: { ...productoDePrueba().categoria, nombre: 'Unisex', linea: 'CALZADO' },
      variantes: [
        {
          id: 'v-40',
          sku: 'PRV-40',
          precio: { valor: 162_000, moneda: 'COP' },
          precioContado: { valor: 162_000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: true,
          atributos: [{ nombre: 'Talla', valor: '40', colorHex: null, unidad: null }],
        },
      ],
    });
    const repositorio: RepositorioProductos = {
      buscar: () =>
        Promise.resolve<ResultadoPaginado<Producto>>({ items: [], cursorSiguiente: null }),
      buscarPorSlug: () => Promise.resolve(tenis()),
    };

    it('pregunta por la talla, no por el color', async () => {
      await renderFicha(repositorio, 'tenis-importados-estilo-nike-air-force-1');
      await screen.findByRole('heading', { name: 'Tenis importados estilo Nike Air Force 1' });

      expect(
        screen.getByText('Tu talla la confirmamos con nuestro proveedor antes de que lo compres.'),
      ).toBeTruthy();
      const chat = screen.getByRole('link', {
        name: 'Consultar disponibilidad por WhatsApp (se abre en otra pestaña)',
      });
      const mensaje = decodeURIComponent((chat.getAttribute('href') ?? '').split('?text=')[1]);
      expect(mensaje).toContain('¿Tienen esta talla disponible?');
      expect(screen.queryByRole('button', { name: 'Agregar al carrito' })).toBeNull();
    });
  });
});
