import { cargadorDeImagenes } from './cargador-de-imagenes';

const VARIANTES = [
  { ancho: 480, url: 'https://x/480.avif' },
  { ancho: 800, url: 'https://x/800.avif' },
  { ancho: 1200, url: 'https://x/1200.avif' },
];

describe('cargadorDeImagenes', () => {
  it('devuelve la URL de la variante del ancho pedido', () => {
    const url = cargadorDeImagenes({
      src: 'https://x/1200.avif',
      width: 800,
      loaderParams: { variantes: VARIANTES },
    });

    expect(url).toBe('https://x/800.avif');
  });

  // Angular llama al loader sin `width` para resolver el `src` de respaldo, que es el que se sirve
  // cuando el navegador no elige.
  it('sin ancho devuelve el src tal cual', () => {
    const url = cargadorDeImagenes({
      src: 'https://x/1200.avif',
      loaderParams: { variantes: VARIANTES },
    });

    expect(url).toBe('https://x/1200.avif');
  });

  // El hero de la portada y la línea del carrito pasan por el mismo loader y no tienen variantes.
  it('sin variantes devuelve el src tal cual', () => {
    expect(cargadorDeImagenes({ src: '/imagenes/portada/hero.webp', width: 1200 })).toBe(
      '/imagenes/portada/hero.webp',
    );
  });

  // `ngSrcset` solo lista anchos que existen, así que esto no debería pasar. Si pasara, servir la
  // imagen base es mejor que construir una URL que nadie sabe si existe.
  it('con un ancho que no existe devuelve el src tal cual', () => {
    const url = cargadorDeImagenes({
      src: 'https://x/1200.avif',
      width: 999,
      loaderParams: { variantes: VARIANTES },
    });

    expect(url).toBe('https://x/1200.avif');
  });
});
