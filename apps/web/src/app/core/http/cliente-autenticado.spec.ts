import { Component, inject } from '@angular/core';
import { render } from '@testing-library/angular';
import { REPOSITORIO_SESION, RepositorioSesion } from '../autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../autenticacion/sesion.model';
import { SesionStore } from '../autenticacion/sesion.store';
import { crearClienteAutenticado } from './cliente-autenticado';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasRefrescar = 0;

  constructor(private sesionAlRefrescar: Sesion | null = null) {}

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en estas pruebas');
  }

  async refrescar(): Promise<Sesion | null> {
    this.llamadasRefrescar++;
    return this.sesionAlRefrescar;
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async cerrarSesion(): Promise<void> {}
}

class RepositorioSesionQueFalla implements RepositorioSesion {
  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en estas pruebas');
  }

  async refrescar(): Promise<Sesion | null> {
    throw new Error('el servidor no respondió');
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async cerrarSesion(): Promise<void> {}
}

@Component({ selector: 'app-anfitrion-de-prueba', template: '' })
class AnfitrionDePrueba {
  readonly store = inject(SesionStore);
}

async function crearSesionStore(repositorio: RepositorioSesion): Promise<SesionStore> {
  const { fixture } = await render(AnfitrionDePrueba, {
    providers: [{ provide: REPOSITORIO_SESION, useValue: repositorio }],
  });
  const store = fixture.componentInstance.store;
  await store.listo;
  return store;
}

function respuestaJson(cuerpo: unknown, estado = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('crearClienteAutenticado', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('agrega Authorization con el token de la sesión', async () => {
    const store = await crearSesionStore(new RepositorioSesionFalso());
    store.sesion.set({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt-valido' });
    let cabeceraVista: string | null = null;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (peticion: Request) => {
        cabeceraVista = peticion.headers.get('Authorization');
        return respuestaJson({ items: [] });
      }),
    );

    const cliente = crearClienteAutenticado('http://localhost', store);
    await cliente.GET('/api/v1/admin/pedidos');

    expect(cabeceraVista).toBe('Bearer jwt-valido');
  });

  it('sin sesión, no agrega Authorization', async () => {
    const store = await crearSesionStore(new RepositorioSesionFalso());
    let cabeceraVista: string | null | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (peticion: Request) => {
        cabeceraVista = peticion.headers.get('Authorization');
        return respuestaJson({ items: [] });
      }),
    );

    const cliente = crearClienteAutenticado('http://localhost', store);
    await cliente.GET('/api/v1/admin/pedidos');

    expect(cabeceraVista).toBeNull();
  });

  it('ante un 401, refresca una vez y reintenta con el token nuevo', async () => {
    const repositorio = new RepositorioSesionFalso({
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt-nuevo',
    });
    const store = await crearSesionStore(repositorio);
    store.sesion.set({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt-viejo' });
    repositorio.llamadasRefrescar = 0; // descarta el refresco silencioso de arranque de SesionStore
    const cabecerasVistas: (string | null)[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (peticion: Request) => {
        cabecerasVistas.push(peticion.headers.get('Authorization'));
        return cabecerasVistas.length === 1 ? new Response(null, { status: 401 }) : respuestaJson({ items: [] });
      }),
    );

    const cliente = crearClienteAutenticado('http://localhost', store);
    const { response } = await cliente.GET('/api/v1/admin/pedidos');

    expect(repositorio.llamadasRefrescar).toBe(1);
    expect(cabecerasVistas).toEqual(['Bearer jwt-viejo', 'Bearer jwt-nuevo']);
    expect(response.status).toBe(200);
  });

  it('si el refresco falla, deja pasar el 401 sin reintentar', async () => {
    const store = await crearSesionStore(new RepositorioSesionQueFalla());
    store.sesion.set({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt-viejo' });
    let llamadas = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        llamadas++;
        return new Response(null, { status: 401 });
      }),
    );

    const cliente = crearClienteAutenticado('http://localhost', store);
    const { response } = await cliente.GET('/api/v1/admin/pedidos');

    expect(llamadas).toBe(1);
    expect(response.status).toBe(401);
  });
});
