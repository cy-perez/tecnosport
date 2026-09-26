import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsAlternadorTema } from './ts-alternador-tema';

function renderAlternador() {
  return render(TsAlternadorTema, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

/**
 * Lo que el servidor o el script en línea de `index.html` ya dejaron puesto
 * cuando este componente se monta. Es el escenario, no el resultado: el
 * componente nunca decide el tema inicial, solo lo lee al alternar.
 */
function conTemaAplicado(tema: 'claro' | 'oscuro') {
  document.documentElement.setAttribute('data-tema', tema);
}

function boton() {
  return screen.getByRole('button', { name: 'Cambiar el tema' });
}

describe('TsAlternadorTema', () => {
  afterEach(() => {
    document.cookie = 'ts-tema=; path=/; max-age=0';
    document.documentElement.removeAttribute('data-tema');
  });

  it('desde claro, el clic pasa a oscuro y lo persiste', async () => {
    conTemaAplicado('claro');
    await renderAlternador();

    fireEvent.click(boton());

    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
    expect(document.cookie).toContain('ts-tema=oscuro');
  });

  it('desde oscuro, el clic vuelve a claro y lo persiste', async () => {
    conTemaAplicado('oscuro');
    await renderAlternador();

    fireEvent.click(boton());

    expect(document.documentElement.getAttribute('data-tema')).toBe('claro');
    expect(document.cookie).toContain('ts-tema=claro');
  });

  // El estado vive en `data-tema` y no en una señal del componente, así que dos
  // clics tienen que ir y volver. Si alguien mete una señal que se desincronice
  // del documento, el segundo clic se queda donde lo dejó el primero.
  it('dos clics devuelven el tema a donde estaba', async () => {
    conTemaAplicado('claro');
    await renderAlternador();

    fireEvent.click(boton());
    fireEvent.click(boton());

    expect(document.documentElement.getAttribute('data-tema')).toBe('claro');
  });

  // Sin `data-tema` el botón tendría que decidir a ciegas. Pasa solo si el
  // script en línea no corrió, y entonces lo que se ve es el tema claro: el
  // botón lleva a oscuro en vez de quedarse sin efecto.
  it('sin tema aplicado, el clic lleva a oscuro', async () => {
    await renderAlternador();

    fireEvent.click(boton());

    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
  });

  // El nombre dice la acción y no el destino, a propósito: el servidor no
  // conoce el tema mientras renderiza, así que un nombre que dependa de él
  // saldría mintiendo hasta hidratar. Que no cambie con el tema es la garantía.
  it('el nombre del botón no depende del tema aplicado', async () => {
    conTemaAplicado('oscuro');
    await renderAlternador();

    fireEvent.click(boton());

    expect(boton()).toBeTruthy();
  });

  it('traduce el nombre del botón al cambiar de idioma', async () => {
    const { fixture } = await renderAlternador();

    fixture.debugElement.injector.get(TranslocoService).setActiveLang('en');

    expect(await screen.findByRole('button', { name: 'Switch theme' })).toBeTruthy();
  });

  /**
   * El anillo es uno u otro, nunca los dos. Nació con las dos clases puestas —`cn` no conoce este
   * par como grupo en conflicto, así que no descartaba la primera— y cuál ganaba lo decidía el
   * orden en la hoja generada. Sobre la franja del pie eso es la diferencia entre un foco visible
   * y uno invisible en tema claro, donde `--color-foco` y `--color-marca` son el mismo grafito.
   *
   * Que el anillo se **vea** hay que mirarlo en el navegador: `:focus-visible` depende de la
   * modalidad y jsdom no la reproduce. Lo que una prueba sí puede fijar es que no salgan los dos.
   */
  it('lleva el anillo de su superficie, y solo uno', async () => {
    const { fixture } = await renderAlternador();
    await fixture.whenStable();

    const clases = () => screen.getByRole('button').getAttribute('class') ?? '';
    expect(clases()).toContain('anillo-foco');
    expect(clases()).not.toContain('anillo-foco-sobre-marca');

    fixture.componentRef.setInput('sobreMarca', true);
    await fixture.whenStable();

    expect(clases()).toContain('anillo-foco-sobre-marca');
    expect(clases().split(/\s+/)).not.toContain('anillo-foco');
  });

  /**
   * Las dos formas del control: el chip con borde y fondo claro del encabezado, y el icono desnudo
   * de la franja del pie, que es el que pinta el pie de referencia de Preline.
   *
   * <p>Lo que la prueba fija no es el aspecto —eso se mira en el navegador— sino que la forma plana
   * **no** se lleva por delante el objetivo táctil. Es la tentación obvia al copiar la referencia,
   * donde el botón mide los 16 px del icono: 44 px es el mínimo de la tabla de medidas, y la
   * excepción "inline" de WCAG 2.5.8 no cubre un botón suelto.
   */
  it('plano quita el borde y el fondo, y conserva el objetivo táctil', async () => {
    const { fixture } = await renderAlternador();
    await fixture.whenStable();

    const clases = () => (screen.getByRole('button').getAttribute('class') ?? '').split(/\s+/);
    expect(clases()).toContain('border');
    expect(clases()).toContain('bg-ts-superficie');
    expect(clases()).toContain('size-tactil');

    fixture.componentRef.setInput('plano', true);
    await fixture.whenStable();

    expect(clases()).toContain('border-0');
    expect(clases()).not.toContain('border');
    expect(clases()).toContain('bg-transparent');
    expect(clases()).not.toContain('bg-ts-superficie');
    expect(clases()).toContain('size-tactil');
  });

  // El icono baja a 16 px con la forma plana, como el `size-4` de la referencia y como los demás
  // iconos del pie. `cn` tiene que descartar el `size-24` por omisión de `ts-icono`, no sumarlo:
  // con los dos puestos, cuál gana lo decide el orden en la hoja generada.
  it('plano baja los iconos a 16 px', async () => {
    const { fixture } = await renderAlternador();
    fixture.componentRef.setInput('plano', true);
    await fixture.whenStable();

    const iconos = Array.from(fixture.nativeElement.querySelectorAll('svg'));
    expect(iconos).toHaveLength(2);
    for (const icono of iconos) {
      const clases = ((icono as SVGElement).getAttribute('class') ?? '').split(/\s+/);
      expect(clases).toContain('size-16');
      expect(clases).not.toContain('size-24');
    }
  });
});
