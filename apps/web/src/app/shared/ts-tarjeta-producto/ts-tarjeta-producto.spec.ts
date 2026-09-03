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
    categoria: { nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes: [
      { id: 'variante-1', sku: 'SKU-1', precio: { valor: 150_000, moneda: 'COP' }, existencia: 3, atributos: [] },
    ],
  };
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
    expect(enlace.getAttribute('href')).toBe('/morral-urbano');
  });
});
