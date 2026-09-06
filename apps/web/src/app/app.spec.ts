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
        provideRouter([]),
        provideTanStackQuery(new QueryClient()),
        { provide: REPOSITORIO_CARRITO, useClass: RepositorioCarritoFalso },
        { provide: REPOSITORIO_SESION, useClass: RepositorioSesionFalso },
      ],
    }).compileComponents();
  });

  it('el encabezado muestra el logo de la marca', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compilado = fixture.nativeElement as HTMLElement;
    expect(compilado.querySelector('img.logo-marca')).toBeTruthy();
  });

  it('el enlace de salto apunta al landmark principal', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compilado = fixture.nativeElement as HTMLElement;

    const salto = compilado.querySelector('a.salto-contenido');
    const principal = compilado.querySelector('main');

    expect(salto?.textContent?.trim()).toBe('Saltar al contenido');
    expect(salto?.getAttribute('href')).toBe('#contenido');
    expect(principal?.id).toBe('contenido');
  });
});
