import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { BotonWhatsapp } from './boton-whatsapp';

async function renderBoton() {
  return render(BotonWhatsapp, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('BotonWhatsapp', () => {
  /**
   * El glifo de WhatsApp es `aria-hidden` —lo es en `ts-icono-marca`, por diseño—, así que el
   * nombre accesible tiene que venir del texto. Si alguien colapsara la etiqueta con `hidden` o
   * `display:none` en vez de con la rejilla, esta prueba lo dice: desaparecería del árbol de
   * accesibilidad y el enlace se quedaría sin nombre.
   */
  it('el enlace tiene nombre accesible aunque la etiqueta esté plegada', async () => {
    await renderBoton();

    expect(screen.getByRole('link', { name: 'Escríbenos por WhatsApp' })).toBeTruthy();
  });

  /**
   * El número sale de `pie.whatsapp_numero` y no escrito aquí: es la única copia que
   * `npm run datos-negocio` cruza contra el resto del sitio y contra los textos legales.
   */
  it('apunta al WhatsApp del negocio con el mensaje ya escrito', async () => {
    await renderBoton();

    const enlace = screen.getByRole('link', { name: 'Escríbenos por WhatsApp' });
    const href = enlace.getAttribute('href') ?? '';

    expect(href.startsWith(`https://wa.me/${es.pie.whatsapp_numero}?text=`)).toBe(true);
    expect(decodeURIComponent(href.split('?text=')[1])).toBe(es.whatsapp.mensaje);
  });

  /**
   * El enlace se recalcula al cambiar de idioma. Es el defecto que `apps/web/CLAUDE.md` describe
   * para `translate()` fuera de una señal: calculado una sola vez, el mensaje se queda en el idioma
   * con el que arrancó la aplicación, y el selector de idioma navega a la misma ruta —Angular
   * reutiliza el componente— así que nadie se entera.
   */
  it('cambia el mensaje al cambiar de idioma', async () => {
    const { fixture } = await renderBoton();

    fixture.debugElement.injector.get(TranslocoService).setActiveLang('en');
    await fixture.whenStable();
    fixture.detectChanges();

    const href = screen.getByRole('link').getAttribute('href') ?? '';
    expect(decodeURIComponent(href.split('?text=')[1])).toBe(en.whatsapp.mensaje);
  });
});
