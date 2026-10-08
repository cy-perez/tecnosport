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
import { CuentaGoogleSinRegistroError } from '../../../../core/autenticacion/sesion.errores';
import { ConfiguracionGoogle } from '../../domain/boton-google.puerto';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import {
  BotonGoogleFalso,
  GOOGLE_HABILITADO,
  proveedoresDeGoogle,
} from '../../../../../testing/google';

class RepositorioSesionFalso implements RepositorioSesion {
  llamadasGoogle: { credencial: string; autorizaDatos: boolean }[] = [];
  errorConGoogle: Error | null = null;

  async iniciarSesionConGoogle(credencial: string, autorizaDatos: boolean): Promise<Sesion> {
    this.llamadasGoogle.push({ credencial, autorizaDatos });
    if (this.errorConGoogle) {
      throw this.errorConGoogle;
    }
    return { usuarioId: 'u1', rol: 'CLIENTE', accessToken: 'jwt' };
  }

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

/** Solo lo que la pantalla pregunta a la cuenta: si hay Google en este ambiente. */
function cuentaConGoogle(configuracion: ConfiguracionGoogle): RepositorioCuenta {
  const noUsado = () => Promise.reject(new Error('no usado en esta prueba'));
  return {
    configuracionGoogle: async () => configuracion,
    registrar: noUsado,
    verificarCorreo: noUsado,
    reenviarVerificacion: noUsado,
    solicitarRecuperacion: noUsado,
    restablecerClave: noUsado,
  };
}

async function renderPagina(
  repositorio: RepositorioSesion,
  boton = new BotonGoogleFalso(),
  configuracion: ConfiguracionGoogle = { habilitado: false, clienteId: '', urlScript: '' },
) {
  return render(IniciarSesionClientePage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'cuenta/es': esCuenta, 'cuenta/en': enCuenta } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      { provide: REPOSITORIO_SESION, useValue: repositorio },
      { provide: REPOSITORIO_CUENTA, useValue: cuentaConGoogle(configuracion) },
      ...proveedoresDeGoogle(boton),
    ],
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

  it('al fallar por campos vacíos lleva el foco al primero con error', async () => {
    await renderPagina(new RepositorioSesionFalso());

    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await vi.waitFor(() =>
      expect(document.activeElement).toBe(screen.getByLabelText('Correo electrónico')),
    );
  });

  /**
   * Mientras se envía, el botón dice que está ocupado pero **no** se deshabilita: deshabilitado
   * bajo el dedo, el foco caía en `<body>` y, si el envío fallaba, el error se oía desde el
   * principio del documento. La guarda de reentrada evita el doble envío.
   */
  it('mientras envía no se deshabilita ni manda dos veces', async () => {
    let llamadas = 0;
    const repositorio = new RepositorioSesionFalso();
    // Una respuesta que no llega: el envío se queda en vuelo durante toda la prueba.
    repositorio.iniciarSesion = () => {
      llamadas++;
      return new Promise(() => undefined);
    };
    const { fixture } = await renderPagina(repositorio);

    await llenarYEnviar();
    await fixture.whenStable();
    const boton = screen.getByRole('button', { name: /entrar|entrando/i });
    expect(boton.hasAttribute('disabled')).toBe(false);
    expect(boton.getAttribute('aria-busy')).toBe('true');

    fireEvent.click(boton);
    expect(llamadas).toBe(1);
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

  /**
   * La etiqueta se ve desde el 4 de octubre de 2026. Estuvo escondida (`sr-only`) con el nombre en
   * el placeholder, que desaparece al primer carácter: quien volvía al campo a medio llenar no
   * tenía en pantalla qué estaba escribiendo (WCAG 3.3.2). Se fija que no vuelva a esconderse.
   */
  it.each([['Correo electrónico'], ['Clave']])(
    '%s tiene la etiqueta a la vista y no la repite como placeholder',
    async (etiqueta) => {
      await renderPagina(new RepositorioSesionFalso());

      const campo = await screen.findByLabelText(etiqueta);
      const rotulo = document.querySelector(`label[for="${campo.id}"]`);
      expect(rotulo?.closest('.sr-only')).toBeNull();
      expect(campo.hasAttribute('placeholder')).toBe(false);
    },
  );

  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPagina(new RepositorioSesionFalso());

    await esperarSinViolaciones(container);
  });

  describe('con Google (ADR-0074)', () => {
    it('sin cliente de Google en el ambiente no pinta el botón', async () => {
      const boton = new BotonGoogleFalso();
      await renderPagina(new RepositorioSesionFalso(), boton);
      await screen.findByLabelText('Correo electrónico');

      expect(boton.pintado).toBe(false);
    });

    /** Desde aquí no se autoriza nada: la credencial viaja con `autorizaDatos` en false. */
    it('entra con la credencial de Google y no autoriza nada desde aquí', async () => {
      const repositorio = new RepositorioSesionFalso();
      const boton = new BotonGoogleFalso();
      const { fixture } = await renderPagina(repositorio, boton, GOOGLE_HABILITADO);
      const router = fixture.debugElement.injector.get(Router);
      const navegar = vi.spyOn(router, 'navigate');
      await vi.waitFor(() => expect(boton.pintado).toBe(true));

      boton.entregar('credencial-de-google');

      await vi.waitFor(() =>
        expect(repositorio.llamadasGoogle).toEqual([
          { credencial: 'credencial-de-google', autorizaDatos: false },
        ]),
      );
      await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['/es']));
    });

    it('sin cuenta, manda a crearla con un enlace', async () => {
      const repositorio = new RepositorioSesionFalso();
      repositorio.errorConGoogle = new CuentaGoogleSinRegistroError();
      const boton = new BotonGoogleFalso();
      await renderPagina(repositorio, boton, GOOGLE_HABILITADO);
      await vi.waitFor(() => expect(boton.pintado).toBe(true));

      boton.entregar('credencial-de-google');

      expect(await screen.findByText(/No hay una cuenta con ese correo de Google/)).toBeTruthy();
      expect(screen.getByRole('link', { name: 'Crea tu cuenta con Google aquí.' })).toBeTruthy();
    });
  });
});
