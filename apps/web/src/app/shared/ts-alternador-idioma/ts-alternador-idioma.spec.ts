import { provideRouter, Router } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsAlternadorIdioma } from './ts-alternador-idioma';

function renderAlternador() {
  return render(TsAlternadorIdioma, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
}

describe('TsAlternadorIdioma', () => {
  it('el idioma activo se marca y no es un control', async () => {
    await renderAlternador();

    // Un solo botón, el del idioma al que se puede ir: el activo es un `<span>`
    // con `aria-current`, como la miga de la página actual en `ts-migas`.
    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByText('ES').getAttribute('aria-current')).toBe('true');
    expect(screen.getByText('ES').tagName).toBe('SPAN');
  });

  it('el botón dice a dónde lleva, no en qué idioma estás', async () => {
    await renderAlternador();

    expect(screen.getByRole('button', { name: 'EN, ver el sitio en inglés' })).toBeTruthy();
  });

  // WCAG 2.5.3: el nombre accesible contiene lo que se ve. "Pulsa EN" por voz no encontraba un
  // botón llamado "Ver el sitio en inglés".
  it('el nombre accesible empieza por el código que se ve', async () => {
    await renderAlternador();

    const boton = screen.getByRole('button');
    expect(boton.getAttribute('aria-label')?.startsWith(boton.textContent!.trim())).toBe(true);
  });

  it('tras cambiar de idioma el foco queda en el segmento activo, no en body', async () => {
    const { fixture } = await renderAlternador();
    const router = fixture.debugElement.injector.get(Router);
    const transloco = fixture.debugElement.injector.get(TranslocoService);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/es/productos');
    vi.spyOn(router, 'navigateByUrl').mockImplementation(async () => {
      transloco.setActiveLang('en');
      return true;
    });

    fireEvent.click(screen.getByRole('button'));

    await vi.waitFor(() => expect(document.activeElement).toBe(screen.getByText('EN')));
  });

  it('el grupo tiene nombre: "ES" y "EN" sueltos no dicen de qué son', async () => {
    await renderAlternador();

    expect(screen.getByRole('group', { name: 'Idioma' })).toBeTruthy();
  });

  it('pulsar navega a la misma ruta con el otro prefijo, no a la portada', async () => {
    const { fixture } = await renderAlternador();
    const router = fixture.debugElement.injector.get(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/es/productos?orden=PRECIO_ASC');
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fireEvent.click(screen.getByRole('button'));

    expect(navegar).toHaveBeenCalledWith('/en/productos?orden=PRECIO_ASC');
  });

  it('sigue al idioma activo cuando cambia desde fuera (un enlace, el botón de atrás)', async () => {
    const { fixture } = await renderAlternador();
    const transloco = fixture.debugElement.injector.get(TranslocoService);
    const navegar = vi.spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl');

    transloco.setActiveLang('en');
    await fixture.whenStable();

    // Se invierten los papeles: ahora el marcado es EN y el botón lleva a ES.
    expect(screen.getByText('EN').getAttribute('aria-current')).toBe('true');
    expect(screen.getByRole('button', { name: 'ES, view the site in Spanish' })).toBeTruthy();
    // Seguir a la URL no debe disparar otra navegación: sería un ciclo.
    expect(navegar).not.toHaveBeenCalled();
  });
});
