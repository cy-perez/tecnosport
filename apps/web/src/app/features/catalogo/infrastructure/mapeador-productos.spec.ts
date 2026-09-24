import type { components } from '@tecnosport/contratos';
import { aProducto } from './mapeador-productos';

describe('mapeador-productos', () => {
  it('mapea un DTO completo al modelo de dominio', () => {
    const dto: components['schemas']['ProductoRespuesta'] = {
      slug: 'camiseta-running',
      nombre: 'Camiseta running',
      descripcion: 'Camiseta transpirable',
      marca: { id: 'm1', nombre: 'TecnoSport' },
      categoria: { nombre: 'Ropa deportiva', slug: 'ropa-deportiva', linea: 'ROPA' },
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
      variantes: [
        {
          id: 'v1',
          sku: 'TS-CAM-AZ-M',
          precio: { valor: 89900, moneda: 'COP' },
          disponible: true,
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
    expect(producto.rotacion).toBeNull();
    expect(producto.variantes).toHaveLength(1);
    expect(producto.variantes[0].id).toBe('v1');
    expect(producto.variantes[0].precio.valor).toBe(89900);
    expect(producto.variantes[0].atributos[0].colorHex).toBe('#1E3A8A');
    // La unidad viaja con el valor; sin ella en el DTO queda en null, no en cadena vacía.
    expect(producto.variantes[0].atributos[0].unidad).toBeNull();
    expect(producto.variantes[0].atributos[1].unidad).toBe('meses');
  });

  it('no truena con campos ausentes del DTO', () => {
    const producto = aProducto({});

    expect(producto.slug).toBe('');
    expect(producto.marca).toEqual({ id: '', nombre: '' });
    expect(producto.imagenPrincipal).toBeNull();
    expect(producto.galeria).toEqual([]);
    expect(producto.variantes).toEqual([]);
  });

  // El orden de los fotogramas es la rotación: uno fuera de sitio se ve como un salto al girar.
  // Se garantiza aquí, en la frontera, para que ninguna pantalla tenga que volver a saberlo.
  /**
   * El contrato declara `variantes` opcional —en el OpenAPI generado todo lo es— y la API nunca
   * publica una imagen sin ellas. Si aun así llegara vacía, servir la imagen en su único ancho
   * conocido es exactamente lo que el sitio hacía antes de que existieran las variantes; no
   * servirla sería peor.
   */
  it('sin variantes en el DTO arma una con el ancho y la URL de la imagen', () => {
    const producto = aProducto({
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

  it('ordena los fotogramas de la rotación por `orden`, llegue como llegue el arreglo', () => {
    const producto = aProducto({
      slug: 'tenis',
      rotacion: {
        fotogramas: 4,
        imagenes: [
          { orden: 2, url: 'f2.jpg', ancho: 1000, alto: 1000 },
          { orden: 0, url: 'f0.jpg', ancho: 1000, alto: 1000 },
          { orden: 3, url: 'f3.jpg', ancho: 1000, alto: 1000 },
          { orden: 1, url: 'f1.jpg', ancho: 1000, alto: 1000 },
        ],
      },
    });

    expect(producto.rotacion?.imagenes.map((imagen) => imagen.orden)).toEqual([0, 1, 2, 3]);
    expect(producto.rotacion?.imagenes.map((imagen) => imagen.url)).toEqual([
      'f0.jpg',
      'f1.jpg',
      'f2.jpg',
      'f3.jpg',
    ]);
  });
});
