import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import es from '../../../../../assets/i18n/es.json';
import en from '../../../../../assets/i18n/en.json';
import { Imagen } from '../../domain/producto.model';
import { TsGaleria } from './ts-galeria';

function imagen(seed: string, altEs: string): Imagen {
  return {
    url: `https://picsum.photos/seed/${seed}/800/600`,
    urlWebp: `https://picsum.photos/seed/${seed}/800/600`,
    ancho: 800,
    alto: 600,
    altEs,
    altEn: altEs,
  };
}

/**
 * `prioritaria` se pasa solo cuando la prueba lo pide. Si el ayudante lo mandara siempre, el valor
 * por omisión del componente no lo ejercitaría nadie y la prueba que lo cubre pasaría igual con el
 * defecto cambiado — comprobado mutándolo.
 */
async function renderGaleria(imagenes: Imagen[], prioritaria?: boolean) {
  return render(TsGaleria, {
    inputs: prioritaria === undefined ? { imagenes } : { imagenes, prioritaria },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('TsGaleria', () => {
  it('muestra la primera imagen como principal', async () => {
    await renderGaleria([imagen('a', 'Foto A'), imagen('b', 'Foto B')]);

    expect(screen.getByRole('img', { name: 'Foto A' })).toBeTruthy();
  });

  it('clic en una miniatura la vuelve la imagen principal', async () => {
    await renderGaleria([imagen('a', 'Foto A'), imagen('b', 'Foto B')]);

    fireEvent.click(screen.getByRole('button', { name: 'Foto B' }));

    expect(screen.getByRole('img', { name: 'Foto B' })).toBeTruthy();
  });

  it('sin más de una imagen no muestra miniaturas', async () => {
    await renderGaleria([imagen('a', 'Foto A')]);

    expect(screen.queryByRole('button')).toBeFalsy();
  });

  // El valor por omisión tiene que ser el que no hace daño: quien se olvide de decidir no se lleva
  // una segunda candidata a LCP compitiendo con la de verdad (NG02955).
  it('por omisión la imagen principal no es prioritaria', async () => {
    await renderGaleria([imagen('a', 'Foto A')]);

    const principal = screen.getByRole('img', { name: 'Foto A' });
    expect(principal.getAttribute('fetchpriority')).toBe('auto');
    expect(principal.getAttribute('loading')).toBe('lazy');
  });

  it('la pantalla puede declararla prioritaria cuando es la candidata a LCP', async () => {
    await renderGaleria([imagen('a', 'Foto A')], true);

    const principal = screen.getByRole('img', { name: 'Foto A' });
    expect(principal.getAttribute('fetchpriority')).toBe('high');
    expect(principal.getAttribute('loading')).toBe('eager');
  });

  it('sirve la WebP, no el original', async () => {
    await renderGaleria([
      {
        url: 'https://imagenes.test/a.jpg',
        urlWebp: 'https://imagenes.test/a.webp',
        ancho: 800,
        alto: 600,
        altEs: 'Foto A',
        altEn: 'Photo A',
      },
    ]);

    expect(screen.getByRole('img', { name: 'Foto A' }).getAttribute('src')).toBe(
      'https://imagenes.test/a.webp',
    );
  });
});
