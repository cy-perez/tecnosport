import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import { TsHero } from './ts-hero';

async function renderHero() {
  const resultado = await render(TsHero, {
    inputs: { idioma: 'es' },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
  await resultado.fixture.whenStable();
  return resultado;
}

/** Los cuatro botones de línea, en el orden de la banda. */
function botonesDeLinea(): HTMLAnchorElement[] {
  return screen
    .getAllByRole('link')
    .filter((enlace): enlace is HTMLAnchorElement =>
      (enlace.getAttribute('href') ?? '').startsWith('/es/productos?linea='),
    );
}

describe('TsHero', () => {
  it('ofrece las cuatro líneas de negocio, cada una a su rejilla filtrada', async () => {
    await renderHero();

    expect(botonesDeLinea().map((enlace) => enlace.getAttribute('href'))).toEqual([
      '/es/productos?linea=ROPA',
      '/es/productos?linea=CALZADO',
      '/es/productos?linea=BOLSOS',
      '/es/productos?linea=TECNOLOGIA',
    ]);
  });

  /**
   * La única afirmación sobre estilo de este archivo, y va contra la regla de "nunca por clase
   * CSS" a sabiendas: no comprueba *qué* clases llevan —eso se mira en el navegador, jsdom no
   * resuelve Tailwind— sino que las cuatro sean **la misma cadena**, que es justo lo que se rompe
   * si alguien vuelve a escribirlas botón por botón. Así se perdió antes el
   * `anillo-foco-sobre-acento` de uno de los cuatro, y así volvió la escala de ámbar que
   * `ADR-0064` retiró.
   */
  it('pinta los cuatro botones exactamente igual', async () => {
    await renderHero();

    const clases = botonesDeLinea().map((enlace) => enlace.getAttribute('class'));
    expect(new Set(clases).size).toBe(1);
  });

  it('enseña los cuatro sellos de confianza', async () => {
    await renderHero();

    expect(screen.getByText('Envíos a todo el país')).toBeTruthy();
    expect(screen.getByText('Opción de pago contraentrega')).toBeTruthy();
    expect(screen.getByText('Diversas opciones de pago')).toBeTruthy();
    expect(screen.getByText('Garantía legal en todo')).toBeTruthy();
  });
});
