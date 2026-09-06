import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import es from '../../../assets/i18n/es.json';
import en from '../../../assets/i18n/en.json';
import { Producto } from '../../features/catalogo/domain/producto.model';
import { TsTarjetaProducto } from './ts-tarjeta-producto';

function productoDePrueba(): Producto {
  return {
    slug: 'morral-urbano',
    nombre: 'Morral urbano',
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: {
      url: 'https://cdn.example.com/morral.jpg',
      urlWebp: 'https://cdn.example.com/morral.webp',
      ancho: 1200,
      alto: 900,
      altEs: 'Morral urbano negro',
      altEn: 'Black urban backpack',
    },
    galeria: [],
    rotacion: null,
    variantes: [
      { id: 'variante-1', sku: 'SKU-1', precio: { valor: 150_000, moneda: 'COP' }, existencia: 3, atributos: [] },
    ],
  };
}

async function renderTarjeta(prioritaria = false) {
  return render(TsTarjetaProducto, {
    inputs: { producto: productoDePrueba(), prioritaria },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
}

describe('TsTarjetaProducto', () => {
  it('enlaza a la ficha del producto por su slug', async () => {
    await render(TsTarjetaProducto, {
      inputs: { producto: productoDePrueba() },
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en },
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([])],
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
