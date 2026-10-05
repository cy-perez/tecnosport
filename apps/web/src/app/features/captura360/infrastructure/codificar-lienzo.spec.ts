import { LienzoCodificable, codificarFotograma } from './codificar-lienzo';

/**
 * Un lienzo que codifica lo que sabe y, para lo que no, hace lo que manda la especificación de
 * HTML: entregar PNG. Es exactamente lo que hace el Safari sin WebP, y es lo que el doble tiene
 * que saber hacer para que la prueba cubra el respaldo.
 */
function lienzoQueSabe(...tipos: string[]): LienzoCodificable & { pedidos: string[] } {
  const pedidos: string[] = [];
  return {
    pedidos,
    toBlob(llamada, tipo = 'image/png') {
      pedidos.push(tipo);
      llamada(new Blob(['x'], { type: tipos.includes(tipo) ? tipo : 'image/png' }));
    },
  };
}

describe('codificarFotograma', () => {
  it('usa WebP cuando el navegador lo codifica', async () => {
    const lienzo = lienzoQueSabe('image/webp', 'image/jpeg');

    const blob = await codificarFotograma(lienzo, 0.8);

    expect(blob?.type).toBe('image/webp');
    expect(lienzo.pedidos).toEqual(['image/webp']);
  });

  it('cae a JPEG cuando WebP llega convertido en PNG', async () => {
    const lienzo = lienzoQueSabe('image/jpeg');

    const blob = await codificarFotograma(lienzo, 0.8);

    expect(blob?.type).toBe('image/jpeg');
    expect(lienzo.pedidos).toEqual(['image/webp', 'image/jpeg']);
  });

  it('devuelve null si no salió ni WebP ni JPEG', async () => {
    expect(await codificarFotograma(lienzoQueSabe(), 0.8)).toBeNull();
  });
});
