import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { CorreoSinVerificarError } from '../../../../core/autenticacion/sesion.errores';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import { IniciarSesionClientePage } from './iniciar-sesion-cliente.page';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  constructor(
    private sesionAlIniciar: Sesion | { error: true } | { errorSinVerificar: true } = {
      usuarioId: 'u1',
      rol: 'CLIENTE',
      accessToken: 'jwt',
    },
  ) {}

  async iniciarSesion(): Promise<Sesion> {
    if ('error' in this.sesionAlIniciar) {
      throw new Error('correo o clave incorrectos');
    }
    if ('errorSinVerificar' in this.sesionAlIniciar) {
      throw new CorreoSinVerificarError();
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


async function renderPagina(repositorio: RepositorioSesion) {
  return render(IniciarSesionClientePage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'cuenta/es': esCuenta, 'cuenta/en': enCuenta } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([]), { provide: REPOSITORIO_SESION, useValue: repositorio }],
  });
}

async function llenarYEnviar() {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'cliente@tecnosport.co' },
  });
  fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('IniciarSesionClientePage', () => {
  it('el botón entrar arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.getByRole('button', { name: 'Entrar' }).hasAttribute('disabled')).toBe(true);
  });

  it('con credenciales válidas de CLIENTE, navega a la portada', async () => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({ usuarioId: 'u1', rol: 'CLIENTE', accessToken: 'jwt' }),
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['/es']));
  });

  it('con una cuenta que es ADMIN, cierra la sesión y muestra el error', async () => {
    const repositorio = new RepositorioSesionFalso({
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt',
    });
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.llamadasCerrar).toBe(1));
    expect(
      screen.getByText(
        'Esta cuenta pertenece al panel administrativo, no a una cuenta de cliente.',
      ),
    ).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con credenciales incorrectas, muestra un error genérico', async () => {
    await renderPagina(new RepositorioSesionFalso({ error: true }));

    await llenarYEnviar();

    expect(await screen.findByText('Correo o clave incorrectos.')).toBeTruthy();
  });

  it('con un correo sin verificar, muestra ese error específico', async () => {
    await renderPagina(new RepositorioSesionFalso({ errorSinVerificar: true }));

    await llenarYEnviar();

    expect(
      await screen.findByText(
        'Verifica tu correo antes de iniciar sesión. Revisa tu bandeja de entrada.',
      ),
    ).toBeTruthy();
  });

  it('muestra un enlace a recuperar clave', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.getByRole('link', { name: '¿Olvidaste tu clave?' })).toBeTruthy();
  });
});
