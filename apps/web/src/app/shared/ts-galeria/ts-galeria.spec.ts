import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import es from '../../../assets/i18n/es.json';
import en from '../../../assets/i18n/en.json';
import { Imagen } from '../../features/catalogo/domain/producto.model';
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

async function renderGaleria(imagenes: Imagen[]) {
  return render(TsGaleria, {
    inputs: { imagenes },
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
});
