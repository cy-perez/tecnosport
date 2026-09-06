import { provideRouter, Router } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsSelectorIdioma } from './ts-selector-idioma';

function renderSelector() {
  return render(TsSelectorIdioma, {
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

describe('TsSelectorIdioma', () => {
  it('arranca en el idioma activo y ofrece los dos', async () => {
    await renderSelector();

    const select = screen.getByLabelText('Idioma') as HTMLSelectElement;

    expect(select.value).toBe('es');
    expect(screen.getByRole('option', { name: 'ES' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'EN' })).toBeTruthy();
  });

  it('elegir el otro idioma navega a la misma ruta con el otro prefijo, no a la portada', async () => {
    const { fixture } = await renderSelector();
    const router = fixture.debugElement.injector.get(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/es/productos?orden=PRECIO_ASC');
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fireEvent.change(screen.getByLabelText('Idioma'), { target: { value: 'en' } });

    expect(navegar).toHaveBeenCalledWith('/en/productos?orden=PRECIO_ASC');
  });

  it('sigue al idioma activo cuando cambia desde fuera (un enlace, el botón de atrás)', async () => {
    const { fixture } = await renderSelector();
    const transloco = fixture.debugElement.injector.get(TranslocoService);
    const navegar = vi.spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl');

    transloco.setActiveLang('en');
    await fixture.whenStable();

    expect((screen.getByLabelText('Idioma') as HTMLSelectElement).value).toBe('en');
    // Seguir a la URL no debe disparar otra navegación: sería un ciclo.
    expect(navegar).not.toHaveBeenCalled();
  });
});
