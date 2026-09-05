import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, convertToParamMap, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { render } from '@testing-library/angular';
import { REPOSITORIO_SESION, RepositorioSesion } from '../../core/autenticacion/repositorio-sesion.puerto';
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
  return { paramMap: convertToParamMap({ lang }), parent: null } as unknown as ActivatedRouteSnapshot;
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
    await configurar(new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }));

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

  it('con sesión de CLIENTE, no permite el acceso', async () => {
    await configurar(new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'CLIENTE', accessToken: 'jwt' }));

    const resultado = await TestBed.runInInjectionContext(() =>
      adminGuard(rutaConLang('es'), {} as RouterStateSnapshot),
    );

    expect(resultado).toBeInstanceOf(UrlTree);
  });
});
