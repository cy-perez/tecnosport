import { aProductoAdminDetalle } from './mapeador-producto-admin';

function detalle(imagenPrincipal: unknown) {
  return {
    id: 'p1',
    nombre: 'Morral urbano',
    descripcion: '',
    slug: 'morral-urbano',
    estado: 'BORRADOR',
    marca: { id: 'm1', nombre: 'TecnoSport' },
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    totalVariantes: 0,
    galeria: [],
    imagenPrincipal,
  } as Parameters<typeof aProductoAdminDetalle>[0];
}

describe('aProductoAdminDetalle', () => {
  /**
   * El detalle dejó de devolver `imagenPrincipalUrl` y pasa la imagen entera (ADR-0057, deuda 24).
   * El panel solo pinta una vista previa, así que la URL sale de la variante mayor — pero si esta
   * derivación se rompe, la pantalla de edición deja de mostrar la foto actual sin que nada falle.
   */
  it('saca la URL de la vista previa de la imagen principal entera', () => {
    const producto = aProductoAdminDetalle(
      detalle({
        url: 'https://x/p-1200.avif',
        variantes: [
          { ancho: 480, url: 'https://x/p-480.avif' },
          { ancho: 1200, url: 'https://x/p-1200.avif' },
        ],
        urlVistaPrevia: 'https://x/p-previa.jpg',
        ancho: 1200,
        alto: 900,
        altEs: 'alt es',
        altEn: 'alt en',
      }),
    );

    expect(producto.imagenPrincipalUrl).toBe('https://x/p-1200.avif');
  });

  it('un producto sin imagen principal la deja en null', () => {
    expect(aProductoAdminDetalle(detalle(undefined)).imagenPrincipalUrl).toBeNull();
  });
});
