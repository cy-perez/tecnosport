import { render, screen } from '@testing-library/angular';
import { TranslocoTestingModule } from '@jsverse/transloco';
import es from '../../../assets/i18n/es.json';
import en from '../../../assets/i18n/en.json';
import { TsPrecio } from './ts-precio';

async function renderPrecio(idioma: 'es' | 'en', valor: number) {
  await render(TsPrecio, {
    inputs: { valor },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: idioma },
        preloadLangs: true,
      }),
    ],
  });
}

describe('TsPrecio', () => {
  it('formatea en pesos con separador de miles de punto en español', async () => {
    await renderPrecio('es', 189900);

    expect(screen.getByText(/189\.900/)).toBeTruthy();
  });

  it('formatea con el código de moneda y separador de coma en inglés', async () => {
    await renderPrecio('en', 189900);

    expect(screen.getByText(/COP/)).toBeTruthy();
    expect(screen.getByText(/189,900/)).toBeTruthy();
  });

  it('nunca convierte el valor: siempre son los mismos pesos', async () => {
    await renderPrecio('en', 89900);

    expect(screen.getByText(/89,900/)).toBeTruthy();
  });
});
