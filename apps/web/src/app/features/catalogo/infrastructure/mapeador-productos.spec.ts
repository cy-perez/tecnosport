import type { components } from '@tecnosport/contratos';
import { aProducto } from './mapeador-productos';

describe('mapeador-productos', () => {
  it('mapea un DTO completo al modelo de dominio', () => {
    const dto: components['schemas']['ProductoRespuesta'] = {
      slug: 'camiseta-running',
      nombre: 'Camiseta running',
      descripcion: 'Camiseta transpirable',
      marca: { id: 'm1', nombre: 'TecnoSport' },
      categoria: { nombre: 'Ropa deportiva', slug: 'ropa-deportiva', linea: 'ROPA_Y_CALZADO' },
      imagenPrincipal: {
        url: 'https://x/0.jpg',
        urlWebp: 'https://x/0.webp',
        ancho: 800,
        alto: 600,
        altEs: 'alt es',
        altEn: 'alt en',
      },
      galeria: [],
      variantes: [
        {
          id: 'v1',
          sku: 'TS-CAM-AZ-M',
          precio: { valor: 89900, moneda: 'COP' },
          existencia: 5,
          atributos: [{ nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A' }],
        },
      ],
    };

    const producto = aProducto(dto);

    expect(producto.slug).toBe('camiseta-running');
    expect(producto.marca.nombre).toBe('TecnoSport');
    expect(producto.imagenPrincipal?.url).toBe('https://x/0.jpg');
    expect(producto.rotacion).toBeNull();
    expect(producto.variantes).toHaveLength(1);
    expect(producto.variantes[0].id).toBe('v1');
    expect(producto.variantes[0].precio.valor).toBe(89900);
    expect(producto.variantes[0].atributos[0].colorHex).toBe('#1E3A8A');
  });

  it('no truena con campos ausentes del DTO', () => {
    const producto = aProducto({});

    expect(producto.slug).toBe('');
    expect(producto.marca).toEqual({ id: '', nombre: '' });
    expect(producto.imagenPrincipal).toBeNull();
    expect(producto.galeria).toEqual([]);
    expect(producto.variantes).toEqual([]);
  });
});
