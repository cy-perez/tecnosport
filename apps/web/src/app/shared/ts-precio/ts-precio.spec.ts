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

describe('TsPrecio con prefijo', () => {
  it('sin prefijo solo muestra el valor', async () => {
    const { container } = await render(TsPrecio, {
      inputs: { valor: 189900 },
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en },
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
    });

    expect(container.querySelectorAll('span')).toHaveLength(1);
  });

  // El prefijo llega traducido desde fuera: el componente no resuelve ninguna
  // clave de i18n, y por eso no puede depender de un scope perezoso.
  it('el prefijo se pinta tal cual lo pasan, sin traducirlo', async () => {
    await render(TsPrecio, {
      inputs: { valor: 189900, prefijo: 'Desde' },
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en },
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
    });

    expect(screen.getByText('Desde')).toBeTruthy();
  });
});
