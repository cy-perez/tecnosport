import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { Miga, TsMigas } from './ts-migas';

@Component({
  imports: [TsMigas],
  template: `<ts-migas [ruta]="ruta" />`,
})
class AnfitrionDePrueba {
  readonly ruta: Miga[] = [
    { etiqueta: 'Portada', enlace: ['/', 'es'] },
    { etiqueta: 'Catálogo', enlace: ['/', 'es', 'productos'] },
    { etiqueta: 'Morral urbano' },
  ];
}

function renderMigas() {
  return render(AnfitrionDePrueba, {
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

describe('TsMigas', () => {
  it('los ancestros son enlaces reales', async () => {
    await renderMigas();

    expect(screen.getByRole('link', { name: 'Portada' }).getAttribute('href')).toBe('/es');
    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe('/es/productos');
  });

  it('la página actual no es un enlace y se marca con aria-current', async () => {
    await renderMigas();

    expect(screen.queryByRole('link', { name: 'Morral urbano' })).toBeNull();
    expect(screen.getByText('Morral urbano').getAttribute('aria-current')).toBe('page');
  });

  it('es una navegación con nombre accesible traducido', async () => {
    await renderMigas();

    expect(screen.getByRole('navigation', { name: 'Ruta de navegación' })).toBeTruthy();
  });
});
