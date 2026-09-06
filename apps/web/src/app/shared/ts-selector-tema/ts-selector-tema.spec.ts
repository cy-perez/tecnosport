import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsSelectorTema } from './ts-selector-tema';

function renderSelector() {
  return render(TsSelectorTema, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

// El entorno de pruebas no trae `matchMedia` (sí el navegador), así que no se
// puede espiar: hay que definirla.
function simularPreferenciaDelSistema(oscuro: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn().mockReturnValue({ matches: oscuro } as MediaQueryList),
  });
}

describe('TsSelectorTema', () => {
  afterEach(() => {
    document.cookie = 'ts-tema=; path=/; max-age=0';
    document.documentElement.removeAttribute('data-tema');
    Reflect.deleteProperty(window, 'matchMedia');
  });

  it('ofrece las tres opciones de docs/04-ui-marca.md', async () => {
    await renderSelector();

    expect(screen.getByRole('option', { name: 'Claro' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Oscuro' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Sistema' })).toBeTruthy();
  });

  it('un tema explícito se aplica al documento y se persiste en la cookie', async () => {
    await renderSelector();

    fireEvent.change(screen.getByLabelText('Tema'), { target: { value: 'oscuro' } });

    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
    expect(document.cookie).toContain('ts-tema=oscuro');
  });

  it('"sistema" se persiste como tal, pero al documento le llega ya resuelto', async () => {
    simularPreferenciaDelSistema(true);
    await renderSelector();

    fireEvent.change(screen.getByLabelText('Tema'), { target: { value: 'sistema' } });

    // La cookie guarda la intención ("sistema"); `data-tema` solo entiende
    // claro u oscuro, así que matchMedia decide cuál.
    expect(document.cookie).toContain('ts-tema=sistema');
    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
  });

  it('con el sistema en claro, "sistema" resuelve a claro', async () => {
    simularPreferenciaDelSistema(false);
    await renderSelector();

    fireEvent.change(screen.getByLabelText('Tema'), { target: { value: 'sistema' } });

    expect(document.documentElement.getAttribute('data-tema')).toBe('claro');
  });

  it('traduce sus opciones al cambiar de idioma', async () => {
    const { fixture } = await renderSelector();

    fixture.debugElement.injector.get(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(screen.getByRole('option', { name: 'Dark' })).toBeTruthy();
  });
});
