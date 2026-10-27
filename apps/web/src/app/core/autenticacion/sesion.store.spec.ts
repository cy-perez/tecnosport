import { Component, inject } from '@angular/core';
import { render } from '@testing-library/angular';
import { REPOSITORIO_SESION, RepositorioSesion } from './repositorio-sesion.puerto';
import { ResultadoInicioSesion, Sesion } from './sesion.model';
import { SesionStore } from './sesion.store';

const SESION_DE_PRUEBA: Sesion = {
  usuarioId: 'usuario-1',
  rol: 'ADMIN',
  accessToken: 'jwt.valido',
  segundoFactorPorActivar: false,
};
const SESION_TRAS_CAMBIAR: Sesion = {
  usuarioId: 'usuario-1',
  rol: 'ADMIN',
  accessToken: 'jwt.nuevo',
  segundoFactorPorActivar: false,
};

class RepositorioSesionFalso implements RepositorioSesion {
  async iniciarSesionConGoogle(): Promise<Sesion> {
    return {
      usuarioId: 'u-google',
      rol: 'CLIENTE',
      accessToken: 'jwt-google',
      segundoFactorPorActivar: false,
    };
  }

  llamadasIniciar = 0;
  llamadasCerrar = 0;
  tokenRecibido: string | null = null;

  constructor(private sesionAlRefrescar: Sesion | null = null) {}

  /** Si está, el primer paso responde con el desafío del segundo factor en vez de la sesión. */
  desafioAlIniciar: string | null = null;
  desafioVerificado: string | null = null;

  async iniciarSesion(): Promise<ResultadoInicioSesion> {
    this.llamadasIniciar++;
    if (this.desafioAlIniciar !== null) {
      return {
        tipo: 'segundo-factor',
        desafio: this.desafioAlIniciar,
        venceEn: '2026-10-27T12:05:00Z',
      };
    }
    return { tipo: 'sesion', sesion: SESION_DE_PRUEBA };
  }

  async verificarSegundoFactor(desafio: string): Promise<Sesion> {
    this.desafioVerificado = desafio;
    return SESION_DE_PRUEBA;
  }

  async cambiarClave(accessToken: string): Promise<Sesion> {
    this.tokenRecibido = accessToken;
    return SESION_TRAS_CAMBIAR;
  }

  llamadasRefrescar = 0;

  async refrescar(): Promise<Sesion | null> {
    this.llamadasRefrescar++;
    return this.sesionAlRefrescar;
  }

  /** Si está, el POST de cierre falla con este error. */
  falloAlCerrar: Error | null = null;

  async cerrarSesion(): Promise<void> {
    this.llamadasCerrar++;
    if (this.falloAlCerrar) {
      throw this.falloAlCerrar;
    }
  }
}

class RepositorioSesionQueFalla implements RepositorioSesion {
  async iniciarSesionConGoogle(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async iniciarSesion(): Promise<ResultadoInicioSesion> {
    throw new Error('correo o clave incorrectos');
  }

  async verificarSegundoFactor(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async cambiarClave(): Promise<Sesion> {
    throw new Error('la clave actual no es correcta');
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

  /**
   * Varias consultas del panel reciben su 401 a la vez. Un refresco por cada una hacía que el
   * servidor viera reutilizado el token y revocara la familia entera: el admin quedaba fuera.
   */
  it('dos refrescos a la vez comparten la misma llamada', async () => {
    const repositorio = new RepositorioSesionFalso(SESION_DE_PRUEBA);
    const { store } = await renderConRepositorio(repositorio);
    await vi.waitFor(() => expect(repositorio.llamadasRefrescar).toBe(1));

    await Promise.all([store.intentarRefrescar(), store.intentarRefrescar()]);

    expect(repositorio.llamadasRefrescar).toBe(2);
  });

  it('iniciarSesion llama al repositorio y deja la sesión', async () => {
    const repositorio = new RepositorioSesionFalso(null);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    const resultado = await store.iniciarSesion('admin@tecnosport.co', 'clave-segura');

    expect(repositorio.llamadasIniciar).toBe(1);
    expect(resultado).toEqual({ tipo: 'sesion', sesion: SESION_DE_PRUEBA });
    expect(store.sesion()).toEqual(SESION_DE_PRUEBA);
    expect(store.esAdmin()).toBe(true);
  });

  /**
   * ADR-0096: con segundo factor, el primer paso no abre sesión —el servidor no manda ni token ni
   * cookie—. Dejar una sesión en la pestaña aquí haría creer al guardia del panel que ya se entró.
   */
  it('con segundo factor, el primer paso devuelve el desafío y no deja sesión', async () => {
    const repositorio = new RepositorioSesionFalso(null);
    repositorio.desafioAlIniciar = 'desafio-1';
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    const resultado = await store.iniciarSesion('admin@tecnosport.co', 'clave-segura');

    expect(resultado.tipo).toBe('segundo-factor');
    expect(store.sesion()).toBeNull();
  });

  it('verificar el segundo factor deja la sesión que devuelve el servidor', async () => {
    const repositorio = new RepositorioSesionFalso(null);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    await store.verificarSegundoFactor('desafio-1', '123456');

    expect(repositorio.desafioVerificado).toBe('desafio-1');
    expect(store.sesion()).toEqual(SESION_DE_PRUEBA);
  });

  /** Activar el factor revoca las sesiones y abre una nueva: la pestaña tiene que quedarse esa. */
  it('adoptar reemplaza la sesión por la nueva', async () => {
    const { store } = await renderConRepositorio(new RepositorioSesionFalso(SESION_DE_PRUEBA));
    await store.listo;

    store.adoptar(SESION_TRAS_CAMBIAR);

    expect(store.sesion()).toEqual(SESION_TRAS_CAMBIAR);
  });

  /** ADR-0074: entrar con Google deja la sesión igual que entrar con clave. */
  it('iniciarSesionConGoogle deja la sesión que devuelve el servidor', async () => {
    const { store } = await renderConRepositorio(new RepositorioSesionFalso(null));
    await store.listo;

    await store.iniciarSesionConGoogle('credencial', true);

    expect(store.sesion()).toEqual({
      usuarioId: 'u-google',
      rol: 'CLIENTE',
      accessToken: 'jwt-google',
      segundoFactorPorActivar: false,
    });
  });

  it('al cambiar la clave, reemplaza la sesión por la que devuelve el servidor', async () => {
    const repositorio = new RepositorioSesionFalso(SESION_DE_PRUEBA);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    await store.cambiarClave('clave-vieja', 'clave-nueva');

    // El servidor revoca todas las sesiones del usuario y abre una nueva. Quedarse con el token
    // viejo dejaría la pantalla con una sesión que el servidor ya no reconoce.
    expect(store.sesion()).toEqual(SESION_TRAS_CAMBIAR);
    expect(repositorio.tokenRecibido).toBe('jwt.valido');
  });

  it('sin sesión abierta no manda nada al servidor', async () => {
    const repositorio = new RepositorioSesionFalso(null);
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    await expect(store.cambiarClave('clave-vieja', 'clave-nueva')).rejects.toThrow();

    expect(repositorio.tokenRecibido).toBeNull();
    expect(store.sesion()).toBeNull();
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

  /**
   * Con el `set` después del `await`, un POST caído dejaba la sesión abierta en pantalla. Se suelta
   * igual, y el fallo llega a quien llama: el servidor no revocó la cookie de refresco.
   */
  it('si el servidor no confirma el cierre, suelta la sesión igual y avisa a quien llama', async () => {
    const repositorio = new RepositorioSesionFalso(SESION_DE_PRUEBA);
    repositorio.falloAlCerrar = new Error('sin conexión');
    const { store } = await renderConRepositorio(repositorio);
    await store.listo;

    await expect(store.cerrarSesion()).rejects.toThrow('sin conexión');

    expect(store.sesion()).toBeNull();
  });
});
