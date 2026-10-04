import { proveerPaletaDePrueba } from '../../../../../testing/paleta-colores';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import es from '../../../../../assets/i18n/es.json';
import en from '../../../../../assets/i18n/en.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Producto } from '../../domain/producto.model';
import { TsTarjetaProducto } from './ts-tarjeta-producto';

function productoDePrueba(): Producto {
  return {
    slug: 'morral-urbano',
    nombre: 'Morral urbano',
    descripcion: '',
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
    imagenPrincipal: {
      url: 'https://cdn.example.com/morral-1200.avif',
      variantes: [
        { ancho: 480, url: 'https://cdn.example.com/morral-480.avif' },
        { ancho: 1200, url: 'https://cdn.example.com/morral-1200.avif' },
      ],
      urlVistaPrevia: null,
      ancho: 1200,
      alto: 900,
      altEs: 'Morral urbano negro',
      altEn: 'Black urban backpack',
      varianteId: null,
    },
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

async function renderTarjeta(prioritaria = false) {
  return render(TsTarjetaProducto, {
    inputs: { producto: productoDePrueba(), prioritaria },
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
      proveerPaletaDePrueba(),
    ],
  });
}

function imagenDePrueba(nombre: string, varianteId: string | null) {
  return {
    url: `https://cdn.example.com/${nombre}-1200.avif`,
    variantes: [{ ancho: 1200, url: `https://cdn.example.com/${nombre}-1200.avif` }],
    urlVistaPrevia: null,
    ancho: 1200,
    alto: 900,
    altEs: `Bodi ${nombre}`,
    altEn: `Bodysuit ${nombre}`,
    varianteId,
  };
}

/** Un bodi de talla única en dos colores, con una foto por color. */
function bodiDePrueba(): Producto {
  const atributos = (color: string, hex: string) => [
    { nombre: 'Color', valor: color, colorHex: hex, unidad: null },
    { nombre: 'Talla', valor: 'Única', colorHex: null, unidad: null },
  ];
  return {
    ...productoDePrueba(),
    slug: 'bodi-herraje',
    nombre: 'Bodi herraje',
    imagenPrincipal: imagenDePrueba('negro', null),
    galeria: [imagenDePrueba('vino', 'v-vino')],
    tallaSirveHasta: 'L',
    variantes: [
      {
        id: 'v-negro',
        sku: 'PRV-1',
        precio: { valor: 60_000, moneda: 'COP' },
        disponible: true,
        atributos: atributos('Negro', '#111111'),
      },
      {
        id: 'v-vino',
        sku: 'PRV-2',
        precio: { valor: 60_000, moneda: 'COP' },
        disponible: true,
        atributos: atributos('Vino', '#722F37'),
      },
    ],
  };
}

async function renderBodi() {
  return render(TsTarjetaProducto, {
    inputs: { producto: bodiDePrueba() },
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
      proveerPaletaDePrueba(),
    ],
  });
}

describe('TsTarjetaProducto', () => {
  /** Con la marca, el nombre y el precio, quien compra no sabía de qué talla era el bodi. */
  it('una prenda de talla única dice su talla y hasta dónde sirve', async () => {
    await renderBodi();

    expect(screen.getByText(/Talla única/)).toBeTruthy();
    expect(screen.getByText(/Sirve hasta: L/)).toBeTruthy();
  });

  it('antepone «Marca:» en negrita al nombre de la marca', async () => {
    await renderTarjeta();

    const etiqueta = screen.getByText('Marca:');
    expect(etiqueta.tagName).toBe('STRONG');
    expect(etiqueta.className).toContain('font-fuerte');
    expect(etiqueta.parentElement!.textContent).toContain('TecnoSport');
  });

  /** 20 px y no 24: el tamaño sale del token del kit, no de la escala de espacio. */
  it('la muestra de color es un círculo del tamaño de la tarjeta', async () => {
    await renderBodi();

    const muestra = screen.getByRole('button', { name: 'Ver en Vino' }).firstElementChild!;
    expect(muestra.className).toContain('size-[var(--control-muestra-tarjeta)]');
    expect(muestra.className).toContain('rounded-completo');
    expect(muestra.className).not.toContain('size-24');
  });

  it('un producto sin talla no dice talla', async () => {
    await renderTarjeta();

    expect(screen.queryByText(/Talla única/)).toBeNull();
  });

  /** Como en Tiendanube: las muestras están fuera del enlace y cambian la foto de la tarjeta. */
  it('elegir un color cambia la foto y no navega', async () => {
    const { container } = await renderBodi();
    const enlace = screen.getByRole('link', { name: /bodi herraje/i });

    expect(container.querySelector('img')!.getAttribute('alt')).toBe('Bodi negro');
    const vino = screen.getByRole('button', { name: 'Ver en Vino' });
    expect(enlace.contains(vino)).toBe(false);

    fireEvent.click(vino);

    expect(vino.getAttribute('aria-pressed')).toBe('true');
    expect(container.querySelector('img')!.getAttribute('alt')).toBe('Bodi vino');
  });

  it('si quedan más de cinco colores, cuenta los que no caben, en singular si es uno', async () => {
    const colores = ['Negro', 'Vino', 'Azul', 'Rojo', 'Verde', 'Gris'];
    const producto = {
      ...bodiDePrueba(),
      variantes: colores.map((color, i) => ({
        id: 'v' + i,
        sku: 'SKU-' + i,
        precio: { valor: 60_000, moneda: 'COP' },
        disponible: true,
        atributos: [{ nombre: 'Color', valor: color, colorHex: '#111111', unidad: null }],
      })),
    };
    await render(TsTarjetaProducto, {
      inputs: { producto },
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
        proveerPaletaDePrueba(),
      ],
    });

    expect(screen.getByText('y 1 color más')).toBeTruthy();
  });

  it('un producto de un solo color no pinta muestras', async () => {
    await renderTarjeta();

    expect(screen.queryByRole('button', { name: /Ver en/ })).toBeNull();
  });

  it('enlaza a la ficha del producto por su slug', async () => {
    await render(TsTarjetaProducto, {
      inputs: { producto: productoDePrueba() },
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
        proveerPaletaDePrueba(),
      ],
    });

    const enlace = screen.getByRole('link', { name: /morral urbano/i });
    expect(enlace.getAttribute('href')).toBe('/es/productos/morral-urbano');
  });

  it('la imagen va en modo fill, sin width ni height propios', async () => {
    // El defecto (NG02952) era declarar el ancho y el alto reales del archivo
    // mientras el SCSS recortaba a un cuadrado: dos relaciones de aspecto que
    // no coinciden. En `fill` el tamaño lo decide el marco. Si alguien vuelve a
    // poner `width`/`height`, Angular lanza en seco y esta prueba lo dice.
    await renderTarjeta();

    const imagen = screen.getByRole('img', { name: 'Morral urbano negro' });
    expect(imagen.hasAttribute('width')).toBe(false);
    expect(imagen.hasAttribute('height')).toBe(false);
    expect((imagen as HTMLElement).style.position).toBe('absolute');
  });

  it('sin marcar, la imagen es perezosa', async () => {
    await renderTarjeta();

    const imagen = screen.getByRole('img', { name: 'Morral urbano negro' });
    expect(imagen.getAttribute('loading')).toBe('lazy');
    expect(imagen.getAttribute('fetchpriority')).toBe('auto');
  });

  it('marcada como LCP, la imagen se pide con prioridad', async () => {
    // El otro defecto del recorrido (NG02955): la portada no tiene <img> de
    // hero, así que su LCP es la primera tarjeta y nadie la estaba priorizando.
    await renderTarjeta(true);

    const imagen = screen.getByRole('img', { name: 'Morral urbano negro' });
    expect(imagen.getAttribute('loading')).toBe('eager');
    expect(imagen.getAttribute('fetchpriority')).toBe('high');
  });
});

describe('TsTarjetaProducto y el prefijo del precio', () => {
  // Antes `ts-precio` resolvía `catalogo.precio_desde` por su cuenta, y esa
  // clave vive en el scope **perezoso** del catálogo. Como `ts-precio` lo
  // comparten checkout y admin, ahí habría pintado la clave cruda. Ahora lo
  // traduce la tarjeta, que sí está dentro del catálogo.
  it('traduce el prefijo y se lo pasa ya resuelto al precio', async () => {
    await renderTarjeta();

    expect(screen.getByText('Desde')).toBeTruthy();
    expect(screen.getByText(/150\.000/)).toBeTruthy();
  });
});
