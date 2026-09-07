import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import enAdmin from '../../../../assets/i18n/scopes/admin/en.json';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../../core/autenticacion/sesion.model';
import { IniciarSesionAdminPage } from './iniciar-sesion-admin.page';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  constructor(
    private sesionAlIniciar: Sesion | { error: true } = {
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt',
    },
  ) {}

  async iniciarSesion(): Promise<Sesion> {
    if ('error' in this.sesionAlIniciar) {
      throw new Error('correo o clave incorrectos');
    }
    return this.sesionAlIniciar;
  }

  async refrescar(): Promise<Sesion | null> {
    return null;
  }

  async cerrarSesion(): Promise<void> {
    this.llamadasCerrar++;
  }
}


async function renderPagina(repositorio: RepositorioSesion, destino?: string) {
  return render(IniciarSesionAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin, 'admin/en': enAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      { provide: REPOSITORIO_SESION, useValue: repositorio },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            queryParamMap: convertToParamMap(destino === undefined ? {} : { destino }),
          },
        },
      },
    ],
  });
}

async function llenarYEnviar() {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'admin@tecnosport.co' },
  });
  fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('IniciarSesionAdminPage', () => {
  it('el botón entrar arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.getByRole('button', { name: 'Entrar' }).hasAttribute('disabled')).toBe(true);
  });

  it('con credenciales válidas de ADMIN, navega al panel', async () => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }),
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
  });

  it('vuelve al destino que puso el guardia, en vez de al panel', async () => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }),
      '/es/admin/productos/abc-123/captura-360',
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/productos/abc-123/captura-360'));
  });

  it.each([
    ['https://sitio-ajeno.example/roba', 'una URL absoluta a otro sitio'],
    ['//sitio-ajeno.example/roba', 'una URL sin esquema, que el navegador resuelve a otro sitio'],
    ['/es/carrito', 'una ruta de este sitio pero fuera de /admin'],
  ])('ignora %s (%s) y va al panel', async (destino) => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }),
      destino,
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
  });

  it('con una cuenta que no es ADMIN, cierra la sesión y muestra el error', async () => {
    const repositorio = new RepositorioSesionFalso({
      usuarioId: 'u1',
      rol: 'CLIENTE',
      accessToken: 'jwt',
    });
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.llamadasCerrar).toBe(1));
    expect(await screen.findByText('Esta cuenta no tiene acceso al panel.')).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con credenciales incorrectas, muestra un error genérico', async () => {
    await renderPagina(new RepositorioSesionFalso({ error: true }));

    await llenarYEnviar();

    expect(await screen.findByText('Correo o clave incorrectos.')).toBeTruthy();
  });
});
