import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { REPOSITORIO_SESION, RepositorioSesion } from '../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../core/autenticacion/sesion.model';
import { SesionStore } from '../../core/autenticacion/sesion.store';
import { Carrito } from '../../features/carrito/domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../../features/carrito/domain/repositorio-carrito.puerto';
import { Encabezado } from './encabezado';

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

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }
  async refrescar(): Promise<Sesion | null> {
    return null;
  }
  async cerrarSesion(): Promise<void> {
    this.llamadasCerrar++;
  }
}

async function renderEncabezado(sesion: Sesion | null = null) {
  const resultado = await render(Encabezado, {
    imports: [
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
  });

  if (sesion) {
    TestBed.inject(SesionStore).sesion.set(sesion);
    await resultado.fixture.whenStable();
  }
  return resultado;
}

describe('Encabezado', () => {
  it('el logo lleva a la portada del idioma activo', async () => {
    await renderEncabezado();

    const logo = screen.getByRole('link', { name: 'Ir a la portada' });

    expect(logo.getAttribute('href')).toBe('/es');
  });

  it('el carrito conserva su nombre accesible aunque su contenido sea un icono', async () => {
    await renderEncabezado();

    const carrito = screen.getByRole('link', { name: 'Carrito, 0 artículos' });

    expect(carrito.getAttribute('href')).toBe('/es/carrito');
    expect(carrito.querySelector('svg')).toBeTruthy();
  });

  it('sin sesión ofrece entrar y crear cuenta, y no muestra el panel', async () => {
    await renderEncabezado();

    expect(screen.getByRole('link', { name: 'Entrar' }).getAttribute('href')).toBe(
      '/es/cuenta/iniciar-sesion',
    );
    expect(screen.getByRole('link', { name: 'Crear cuenta' }).getAttribute('href')).toBe(
      '/es/cuenta/registro',
    );
    expect(screen.queryByRole('link', { name: 'Panel' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Cerrar sesión' })).toBeNull();
  });

  it('con sesión de ADMIN muestra el panel y ofrece cerrar sesión', async () => {
    await renderEncabezado({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' });

    expect(screen.getByRole('link', { name: 'Panel' }).getAttribute('href')).toBe('/es/admin');
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Entrar' })).toBeNull();
  });

  it('con sesión de CLIENTE no muestra el panel de administración', async () => {
    await renderEncabezado({ usuarioId: 'u2', rol: 'CLIENTE', accessToken: 'jwt' });

    expect(screen.queryByRole('link', { name: 'Panel' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeTruthy();
  });

  it('el catálogo es alcanzable desde el encabezado', async () => {
    await renderEncabezado();

    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe('/es/productos');
  });
});
