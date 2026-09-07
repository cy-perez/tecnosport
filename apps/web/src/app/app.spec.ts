import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import es from '../assets/i18n/es.json';
import en from '../assets/i18n/en.json';
import { App } from './app';
import { Carrito } from './features/carrito/domain/carrito.model';
import { REPOSITORIO_SESION, RepositorioSesion } from './core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from './core/autenticacion/sesion.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from './features/carrito/domain/repositorio-carrito.puerto';
import { esperarSinViolaciones } from '../testing/axe';
import { proveerAlmacenesCarrito } from '../testing/carrito';

class RepositorioCarritoFalso implements RepositorioCarrito {
  crear(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  ver(): Promise<Carrito | null> {
    return Promise.resolve(null);
  }
  agregarLinea(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  actualizarCantidad(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
  eliminarLinea(): Promise<Carrito> {
    return Promise.reject(new Error('no usado en esta prueba'));
  }
}

// El encabezado ahora refleja la sesión: sin este doble, `SesionStore` no
// se puede construir.
class RepositorioSesionFalso implements RepositorioSesion {
  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }
  async refrescar(): Promise<Sesion | null> {
    return null;
  }
  async cerrarSesion(): Promise<void> {
    // Sin sesión en estas pruebas: no hay nada que cerrar.
  }
}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        App,
        TranslocoTestingModule.forRoot({
          langs: { es, en },
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [
      ...proveerAlmacenesCarrito(),
        provideRouter([]),
        provideTanStackQuery(new QueryClient()),
        { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
        { provide: REPOSITORIO_SESION, useClass: RepositorioSesionFalso },
      ],
    }).compileComponents();
  });

  // Consultaba `img.logo-marca`, una clase que existía solo para el SCSS del
  // encabezado y que desapareció al pasarlo a Tailwind. Consultar por clase CSS
  // es justo lo que `docs/06-testing.md` prohíbe: la prueba se rompía por un
  // cambio de estilos sin que nada del comportamiento hubiera cambiado. Ahora
  // comprueba lo que de verdad importa, que son los dos ejes del logo.
  it('el encabezado sirve el logo en sus cuatro variantes de tamaño y tema', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compilado = fixture.nativeElement as HTMLElement;

    const fuentes = [...compilado.querySelectorAll('header img')].map((img) =>
      img.getAttribute('src'),
    );

    // En móvil el isotipo, desde el primer punto de quiebre el horizontal
    // (docs/04-ui-marca.md: por debajo de 160 px de ancho, isotipo solo).
    expect(fuentes).toContain('assets/marca/logo/isotipo.svg');
    expect(fuentes).toContain('assets/marca/logo/logo-horizontal.svg');
    // El logo es monocromo: cada tamaño tiene su versión negativa, y el tema
    // decide cuál se ve.
    expect(fuentes).toContain('assets/marca/logo/isotipo-negativo.svg');
    expect(fuentes).toContain('assets/marca/logo/logo-mono-negativo.svg');
  });

  it('el enlace de salto apunta al landmark principal', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compilado = fixture.nativeElement as HTMLElement;

    // Por texto accesible y no por `a.salto-contenido`: esa clase existía solo
    // para el SCSS y desapareció al pasar el shell a Tailwind. Lo que la prueba
    // defiende es que el enlace exista, diga lo que dice y apunte al landmark.
    const salto = [...compilado.querySelectorAll('a')].find(
      (a) => a.textContent?.trim() === 'Saltar al contenido',
    );
    const principal = compilado.querySelector('main');

    expect(salto?.textContent?.trim()).toBe('Saltar al contenido');
    expect(salto?.getAttribute('href')).toBe('#contenido');
    expect(principal?.id).toBe('contenido');
  });

  // El cascarón está en todas las pantallas, así que una violación aquí las
  // afecta a todas: encabezado, enlace de salto, landmark principal y pie.
  it('el cascarón no tiene violaciones de WCAG 2.2 AA', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    await esperarSinViolaciones(fixture.nativeElement as HTMLElement);
  });
});
