import { Component, PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { render } from '@testing-library/angular';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../core/autenticacion/sesion.model';
import { adminGuard } from './admin.guard';

class RepositorioSesionFalso implements RepositorioSesion {
  constructor(private sesionAlRefrescar: Sesion | null) {}

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async refrescar(): Promise<Sesion | null> {
    return this.sesionAlRefrescar;
  }

  async cerrarSesion(): Promise<void> {
    return;
  }
}

function rutaConLang(lang: string): ActivatedRouteSnapshot {
  return {
    paramMap: convertToParamMap({ lang }),
    parent: null,
  } as unknown as ActivatedRouteSnapshot;
}

@Component({ selector: 'app-anfitrion-de-prueba', template: '' })
class AnfitrionDePrueba {}

/** Renderizar un componente real (no solo `TestBed.runInInjectionContext`)
 * es necesario para que `afterNextRender` de `SesionStore` de verdad
 * dispare — sin un render real, `listo` nunca se resuelve y la prueba
 * se cuelga. */
async function configurar(repositorio: RepositorioSesion) {
  await render(AnfitrionDePrueba, {
    providers: [provideRouter([]), { provide: REPOSITORIO_SESION, useValue: repositorio }],
  });
}

describe('adminGuard', () => {
  it('con sesión de ADMIN, permite el acceso', async () => {
    await configurar(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }),
    );

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('es'), {} as RouterStateSnapshot),
    );

    expect(resultado).toBe(true);
  });

  it('sin sesión, redirige a iniciar-sesion con el prefijo de idioma de la ruta', async () => {
    await configurar(new RepositorioSesionFalso(null));

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('en'), {} as RouterStateSnapshot),
    );

    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/en/admin/iniciar-sesion');
  });

  it('al redirigir, recuerda a dónde iba', async () => {
    await configurar(new RepositorioSesionFalso(null));

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('es'), {
        url: '/es/admin/productos/abc-123/captura-360',
      } as RouterStateSnapshot),
    );

    // Sin esto, abrir un enlace directo sin sesión terminaba en el panel y había
    // que volver a buscar el enlace.
    expect((resultado as UrlTree).toString()).toBe(
      '/es/admin/iniciar-sesion?destino=%2Fes%2Fadmin%2Fproductos%2Fabc-123%2Fcaptura-360',
    );
  });

  it('con sesión de CLIENTE, no permite el acceso', async () => {
    await configurar(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'CLIENTE', accessToken: 'jwt' }),
    );

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('es'), {} as RouterStateSnapshot),
    );

    expect(resultado).toBeInstanceOf(UrlTree);
  });

  // En SSR el guardia no decide: SesionStore resuelve siempre "sin sesión" en
  // el servidor a propósito (no reenvía la cookie HttpOnly de refresco), así
  // que decidir con eso redirigía SIEMPRE al login. El navegador seguía ese
  // 302 e hidrataba ya en la pantalla de login: recargar cualquier página de
  // /admin sacaba al administrador con la sesión viva. Encontrado en el
  // navegador; ninguna de las pruebas de arriba lo veía porque todas corren
  // como si fueran el navegador.
  it('en el servidor deja pasar, para que decida el cliente tras el refresco', async () => {
    await render(AnfitrionDePrueba, {
      providers: [
        provideRouter([]),
        { provide: REPOSITORIO_SESION, useValue: new RepositorioSesionFalso(null) },
        { provide: PLATFORM_ID, useValue: 'server' },
      ],
    });

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('es'), {} as RouterStateSnapshot),
    );

    expect(resultado).toBe(true);
  });
});
