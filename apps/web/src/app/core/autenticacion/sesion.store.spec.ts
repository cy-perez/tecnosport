import { Component, inject } from '@angular/core';
import { render } from '@testing-library/angular';
import { REPOSITORIO_SESION, RepositorioSesion } from './repositorio-sesion.puerto';
import { Sesion } from './sesion.model';
import { SesionStore } from './sesion.store';

const SESION_DE_PRUEBA: Sesion = { usuarioId: 'usuario-1', rol: 'ADMIN', accessToken: 'jwt.valido' };

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasIniciar = 0;
  llamadasCerrar = 0;

  constructor(private sesionAlRefrescar: Sesion | null = null) {}

  async iniciarSesion(): Promise<Sesion> {
    this.llamadasIniciar++;
    return SESION_DE_PRUEBA;
  }

  async refrescar(): Promise<Sesion | null> {
    return this.sesionAlRefrescar;
  }

  async cerrarSesion(): Promise<void> {
    this.llamadasCerrar++;
  }
}

class RepositorioSesionQueFalla implements RepositorioSesion {
  async iniciarSesion(): Promise<Sesion> {
    throw new Error('correo o clave incorrectos');
  }

  async refrescar(): Promise<Sesion | null> {
    throw new Error('el servidor no respondió');
  }

  async cerrarSesion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

@Component({ selector: 'app-anfitrion-de-prueba', template: '' })
class AnfitrionDePrueba {
  readonly store = inject(SesionStore);
}

async function renderConRepositorio(repositorio: RepositorioSesion) {
  const { fixture } = await render(AnfitrionDePrueba, {
    providers: [{ provide: REPOSITORIO_SESION, useValue: repositorio }],
  });
  return { store: fixture.componentInstance.store };
}

describe('SesionStore', () => {
  it('sin cookie de refresco válida, arranca sin sesión', async () => {
    const { store } = await renderConRepositorio(new RepositorioSesionFalso(null));

    await store.listo;

    expect(store.sesion()).toBeNull();
    expect(store.esAdmin()).toBe(false);
  });

  it('con una cookie de refresco válida, arranca ya con sesión (refresco silencioso)', async () => {
    const { store } = await renderConRepositorio(new RepositorioSesionFalso(SESION_DE_PRUEBA));

    await store.listo;

    expect(store.sesion()).toEqual(SESION_DE_PRUEBA);
    expect(store.esAdmin()).toBe(true);
  });

  it('si el refresco del arranque falla, queda sin sesión en vez de romper', async () => {
    const { store } = await renderConRepositorio(new RepositorioSesionQueFalla());

    await store.listo;

    expect(store.sesion()).toBeNull();
  });

  it('iniciarSesion llama al repositorio y deja la sesión', async () => {
    const repositorio = new RepositorioSesionFalso(null);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    const sesion = await store.iniciarSesion('admin@tecnosport.co', 'clave-segura');

    expect(repositorio.llamadasIniciar).toBe(1);
    expect(store.sesion()).toEqual(sesion);
    expect(store.esAdmin()).toBe(true);
  });

  it('cerrarSesion llama al repositorio y limpia la sesión', async () => {
    const repositorio = new RepositorioSesionFalso(SESION_DE_PRUEBA);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;
    expect(store.sesion()).not.toBeNull();

    await store.cerrarSesion();

    expect(repositorio.llamadasCerrar).toBe(1);
    expect(store.sesion()).toBeNull();
  });
});
