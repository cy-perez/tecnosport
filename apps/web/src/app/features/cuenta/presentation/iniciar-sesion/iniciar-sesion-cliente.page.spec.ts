import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { CorreoSinVerificarError } from '../../../../core/autenticacion/sesion.errores';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import { IniciarSesionClientePage } from './iniciar-sesion-cliente.page';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasCerrar = 0;

  constructor(
    private sesionAlIniciar:
      Sesion | { error: true } | { errorSinVerificar: true } | { falloServidor: true } = {
      usuarioId: 'u1',
      rol: 'CLIENTE',
      accessToken: 'jwt',
    },
  ) {}

  async iniciarSesion(): Promise<Sesion> {
    // Lo que lanza el adaptador de verdad ante credenciales malas es un `ErrorHttp` 401
    // (`sesion-http.repositorio.ts`), no un `Error` pelado: el doble lo imita para que la
    // pantalla se pruebe contra la forma real del fallo.
    if ('error' in this.sesionAlIniciar) {
      throw new ErrorHttp(401, 'no se pudo iniciar sesión');
    }
    if ('falloServidor' in this.sesionAlIniciar) {
      throw new ErrorHttp(500, 'no se pudo iniciar sesión');
    }
    if ('errorSinVerificar' in this.sesionAlIniciar) {
      throw new CorreoSinVerificarError();
    }
    return this.sesionAlIniciar;
  }

  async cambiarClave(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
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
  /**
   * El boton ya no arranca deshabilitado: se pulsa, se marcan los campos y se dice que falta. Un
   * `<button disabled>` sale del orden de tabulacion, asi que quien navega con teclado no lo
   * encuentra y nada le explica por que no pasa nada (`apps/web/CLAUDE.md`). Mismo criterio que
   * `crear-producto-admin`.
   */
  it('con el formulario vacío dice qué falta y no envía nada', async () => {
    await renderPagina(new RepositorioSesionFalso());

    const boton = screen.getByRole('button', { name: 'Entrar' });
    expect(boton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(boton);

    expect(await screen.findByText('Escribe tu correo.')).toBeTruthy();
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

  it('con el servidor caído, no le echa la culpa al correo ni a la clave', async () => {
    await renderPagina(new RepositorioSesionFalso({ falloServidor: true }));

    await llenarYEnviar();

    expect(
      await screen.findByText(
        'No pudimos conectarnos con el servidor. Intenta de nuevo en unos minutos.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Correo o clave incorrectos.')).toBeNull();
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

  // Quien llega sin cuenta no tenía por dónde crearla desde aquí: el único
  // enlace de registro estaba en el encabezado.
  it('muestra un enlace a crear cuenta', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.getByRole('link', { name: 'Crear cuenta' })).toBeTruthy();
  });

  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPagina(new RepositorioSesionFalso());

    await esperarSinViolaciones(container);
  });
});
