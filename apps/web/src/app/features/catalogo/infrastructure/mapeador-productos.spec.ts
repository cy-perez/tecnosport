import type { components } from '@tecnosport/contratos';
import { aProducto } from './mapeador-productos';

describe('mapeador-productos', () => {
  it('mapea un DTO completo al modelo de dominio', () => {
    const dto: components['schemas']['ProductoRespuesta'] = {
      slug: 'camiseta-running',
      nombre: 'Camiseta running',
      descripcion: 'Camiseta transpirable',
      marca: { id: 'm1', nombre: 'TecnoSport' },
      categoria: { nombre: 'Camisetas', slug: 'ropa-caballero-camisetas', linea: 'ROPA' },
      imagenPrincipal: {
        url: 'https://x/0-800.avif',
        variantes: [
          { ancho: 480, url: 'https://x/0-480.avif' },
          { ancho: 800, url: 'https://x/0-800.avif' },
        ],
        ancho: 800,
        alto: 600,
        altEs: 'alt es',
        altEn: 'alt en',
      },
      galeria: [],
      paleta: ['Morado', 'Plata'],
      variantes: [
        {
          id: 'v1',
          sku: 'TS-CAM-AZ-M',
          precio: { valor: 89900, moneda: 'COP' },
          precioContado: { valor: 86000, moneda: 'COP' },
          disponible: true,
          disponibilidadPorConfirmar: false,
          atributos: [
            { nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A' },
            { nombre: 'Garantía', valor: '12', unidad: 'meses' },
          ],
        },
      ],
    };

    const producto = aProducto(dto);

    expect(producto.slug).toBe('camiseta-running');
    expect(producto.marca.nombre).toBe('TecnoSport');
    expect(producto.imagenPrincipal?.url).toBe('https://x/0-800.avif');
    expect(producto.imagenPrincipal?.variantes.map((variante) => variante.ancho)).toEqual([
      480, 800,
    ]);
    expect(producto.variantes).toHaveLength(1);
    expect(producto.variantes[0].id).toBe('v1');
    expect(producto.variantes[0].precio.valor).toBe(89900);
    expect(producto.variantes[0].precioContado).toEqual({ valor: 86000, moneda: 'COP' });
    expect(producto.paleta).toEqual(['Morado', 'Plata']);
    expect(producto.variantes[0].disponibilidadPorConfirmar).toBe(false);
    expect(producto.variantes[0].atributos[0].colorHex).toBe('#1E3A8A');
    // La unidad viaja con el valor; sin ella en el DTO queda en null, no en cadena vacía.
    expect(producto.variantes[0].atributos[0].unidad).toBeNull();
    expect(producto.variantes[0].atributos[1].unidad).toBe('meses');
  });

  it('no truena con campos ausentes del DTO', () => {
    const producto = aProducto({ paleta: [] });

    expect(producto.slug).toBe('');
    expect(producto.marca).toEqual({ id: '', nombre: '' });
    expect(producto.imagenPrincipal).toBeNull();
    expect(producto.galeria).toEqual([]);
    expect(producto.variantes).toEqual([]);
  });

  /**
   * El contrato declara `variantes` opcional —en el OpenAPI generado todo lo es— y la API nunca
   * publica una imagen sin ellas. Si aun así llegara vacía, servir la imagen en su único ancho
   * conocido es exactamente lo que el sitio hacía antes de que existieran las variantes; no
   * servirla sería peor.
   */
  it('sin variantes en el DTO arma una con el ancho y la URL de la imagen', () => {
    const producto = aProducto({
      paleta: [],
      slug: 'camiseta',
      imagenPrincipal: {
        url: 'https://x/unica.avif',
        ancho: 640,
        alto: 480,
        altEs: 'alt es',
        altEn: 'alt en',
      },
    });

    expect(producto.imagenPrincipal?.variantes).toEqual([
      { ancho: 640, url: 'https://x/unica.avif' },
    ]);
  });
});
