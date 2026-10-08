import { ConfiguracionGoogle } from '../../domain/boton-google.puerto';
import { vi } from 'vitest';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { DemasiadosIntentosError } from '../../../../core/autenticacion/sesion.errores';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { RegistroClientePage } from './registro-cliente.page';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../core/autenticacion/repositorio-sesion.puerto';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import {
  BotonGoogleFalso,
  GOOGLE_HABILITADO,
  proveedoresDeGoogle,
} from '../../../../../testing/google';

class RepositorioCuentaFalso implements RepositorioCuenta {
  configuracion: ConfiguracionGoogle = { habilitado: false, clienteId: '', urlScript: '' };

  async configuracionGoogle(): Promise<ConfiguracionGoogle> {
    return this.configuracion;
  }

  llamadasRegistrar: { correo: string; clave: string; autorizaDatos: boolean }[] = [];

  constructor(
    private errorAlRegistrar:
      | 'correo-registrado'
      | 'generico'
      | 'codigo-correo-invalido'
      | 'demasiados-intentos'
      | null = null,
  ) {}

  /** Lo que de verdad importa comprobar: que la autorización viaja, y no que el servidor la
   * suponga. Un `true` por omisión en el cliente sería una autorización inventada. */
  get autorizacionRecibida(): boolean {
    return this.llamadasRegistrar.at(-1)?.autorizaDatos ?? false;
  }

  async registrar(correo: string, clave: string, autorizaDatos: boolean): Promise<void> {
    this.llamadasRegistrar.push({ correo, clave, autorizaDatos });
    if (this.errorAlRegistrar === 'correo-registrado') {
      throw new CorreoYaRegistradoError();
    }
    if (this.errorAlRegistrar === 'generico') {
      throw new Error('falló');
    }
    // Lo que de verdad devuelve el backend cuando el correo no tiene forma: un 422 con su
    // `codigo` en el `ProblemDetail`. El doble lanzaba un `Error` pelado, que es justo por lo que
    // nadie notó que la página tiraba ese código a la basura.
    if (this.errorAlRegistrar === 'codigo-correo-invalido') {
      throw new ErrorHttp(422, 'no se pudo crear la cuenta', 'CORREO_ELECTRONICO_INVALIDO');
    }
    if (this.errorAlRegistrar === 'demasiados-intentos') {
      throw new DemasiadosIntentosError();
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async verificarCorreo(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async solicitarRecuperacion(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async restablecerClave(): Promise<void> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async reenviarVerificacion(): Promise<void> {}
}

/** La sesión, para la cuenta que nace con Google: anota con qué autorización llegó. */
class SesionConGoogleFalsa implements RepositorioSesion {
  llamadasGoogle: { credencial: string; autorizaDatos: boolean }[] = [];

  async iniciarSesionConGoogle(credencial: string, autorizaDatos: boolean): Promise<Sesion> {
    this.llamadasGoogle.push({ credencial, autorizaDatos });
    return { usuarioId: 'u1', rol: 'CLIENTE', accessToken: 'jwt' };
  }

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async cambiarClave(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async refrescar(): Promise<Sesion | null> {
    return null;
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async cerrarSesion(): Promise<void> {}
}

async function renderPagina(
  repositorio: RepositorioCuenta,
  sesion: RepositorioSesion = new SesionConGoogleFalsa(),
  boton = new BotonGoogleFalso(),
) {
  return render(RegistroClientePage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'cuenta/es': esCuenta, 'cuenta/en': enCuenta } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      { provide: REPOSITORIO_CUENTA, useValue: repositorio },
      { provide: REPOSITORIO_SESION, useValue: sesion },
      ...proveedoresDeGoogle(boton),
    ],
  });
}

const ETIQUETA_AUTORIZACION =
  'Autorizo el tratamiento de mis datos personales para crear mi cuenta.';

function llenarCampos(clave = 'clave-segura', confirmarClave = 'clave-segura') {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'cliente@tecnosport.co' },
  });
  fireEvent.input(screen.getByLabelText('Clave'), { target: { value: clave } });
  fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: confirmarClave } });
}

async function llenarYEnviar(clave = 'clave-segura', confirmarClave = 'clave-segura') {
  llenarCampos(clave, confirmarClave);
  fireEvent.click(screen.getByLabelText(ETIQUETA_AUTORIZACION));
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
}

describe('RegistroClientePage', () => {
  it('muestra un enlace a iniciar sesión, para quien ya tiene cuenta', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toBeTruthy();
  });

  /**
   * El boton ya no arranca deshabilitado: se pulsa, se marcan los campos y se dice que falta. Un
   * `<button disabled>` sale del orden de tabulacion, asi que quien navega con teclado no lo
   * encuentra y nada le explica por que no pasa nada (`apps/web/CLAUDE.md`). Mismo criterio que
   * `crear-producto-admin`.
   */
  it('con el formulario vacío dice qué falta y no envía nada', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    const boton = screen.getByRole('button', { name: 'Crear cuenta' });
    expect(boton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(boton);

    expect(await screen.findByText('Escribe tu correo.')).toBeTruthy();
  });

  it('registrar exitosamente muestra el mensaje de revisa tu correo', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.llamadasRegistrar).toHaveLength(1));

    expect(repositorio.llamadasRegistrar).toEqual([
      { correo: 'cliente@tecnosport.co', clave: 'clave-segura', autorizaDatos: true },
    ]);
    expect(await screen.findByText('Revisa tu correo')).toBeTruthy();
  });

  it('con un correo ya registrado, muestra ese error específico', async () => {
    await renderPagina(new RepositorioCuentaFalso('correo-registrado'));

    await llenarYEnviar();

    expect(await screen.findByText('Ya existe una cuenta con ese correo.')).toBeTruthy();
  });

  it('con un error genérico del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioCuentaFalso('generico'));

    await llenarYEnviar();

    expect(await screen.findByText('No se pudo crear la cuenta. Intenta de nuevo.')).toBeTruthy();
  });

  /**
   * El defecto que trajo esta tanda: el `errorCorreo()` solo sabía decir "escribe tu correo", así
   * que un correo con forma inválida dejaba el formulario `INVALID` y la pantalla **muda**. Se
   * pulsaba "Crear cuenta" y no pasaba nada: ni mensaje, ni avance, ni petición.
   */
  it.each([['1234'], ['@#$%'], ['juan'], ['juan@correo']])(
    'con el correo "%s" dice que la forma no sirve, en vez de quedarse mudo',
    async (correo) => {
      const repositorio = new RepositorioCuentaFalso();
      await renderPagina(repositorio);

      fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: correo } });
      fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
      fireEvent.input(screen.getByLabelText('Confirmar clave'), {
        target: { value: 'clave-segura' },
      });
      fireEvent.click(screen.getByLabelText(ETIQUETA_AUTORIZACION));
      fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

      expect(await screen.findByText('Ese correo no tiene una forma válida.')).toBeTruthy();
      expect(repositorio.llamadasRegistrar).toEqual([]);
    },
  );

  /**
   * `juan@correo` merece prueba propia: es la franja que `Validators.email` daba por buena y
   * `CorreoElectronico.java` rechaza, o sea la que llegaba hasta el servidor y volvía como el
   * mensaje genérico. Ahora ni sale de aquí.
   */
  it('el dominio sin punto no llega al servidor', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'juan@correo' },
    });
    fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
    fireEvent.input(screen.getByLabelText('Confirmar clave'), {
      target: { value: 'clave-segura' },
    });
    fireEvent.click(screen.getByLabelText(ETIQUETA_AUTORIZACION));
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Ese correo no tiene una forma válida.')).toBeTruthy();
    expect(repositorio.llamadasRegistrar).toEqual([]);
  });

  /**
   * Y si aun así el servidor rechaza el correo —otro cliente, otra regla—, se dice **por qué**.
   * Antes ese 422 se leía "No se pudo crear la cuenta. Intenta de nuevo.", que no dice qué
   * corregir y manda a repetir lo que va a fallar igual.
   */
  it('un 422 por el formato del correo dice cuál es el problema, no el genérico', async () => {
    await renderPagina(new RepositorioCuentaFalso('codigo-correo-invalido'));

    await llenarYEnviar();

    expect(
      await screen.findByText(
        'Ese correo no tiene una forma válida. Revísalo y vuelve a intentarlo.',
      ),
    ).toBeTruthy();
  });

  it('el límite de intentos se distingue de una caída del servidor', async () => {
    await renderPagina(new RepositorioCuentaFalso('demasiados-intentos'));

    await llenarYEnviar();

    expect(
      await screen.findByText(
        'Demasiados intentos seguidos. Espera unos minutos y vuelve a probar.',
      ),
    ).toBeTruthy();
  });

  it('con claves que no coinciden, muestra el error de confirmación', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
    fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: 'otra-clave' } });
    expect(await screen.findByText('Las claves no coinciden.')).toBeTruthy();
    // El boton ya no se deshabilita: lo que impide enviar es la guarda de `enviar()`, y lo
    // que lo explica es el mensaje de arriba.
    expect(screen.getByRole('button', { name: 'Crear cuenta' }).hasAttribute('disabled')).toBe(
      false,
    );
  });

  // La autorización es un consentimiento aparte y sin él no hay cuenta (Ley 1581 de 2012). El
  // servidor lo exige igual; esto es para que el comprador no llegue hasta el 422.
  /**
   * La autorizacion ya no se exige deshabilitando el boton: se pulsa y se dice que falta. Es el
   * mismo arreglo que el checkout ya recibio una vez —"Sin marcar la casilla, «Continuar» no hacia
   * nada y no decia por que"— y que aqui seguia pendiente.
   */
  it('sin marcar la autorización de datos, dice que falta y no registra nada', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);
    llenarCampos();

    const boton = screen.getByRole('button', { name: 'Crear cuenta' });
    expect(boton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(boton);

    expect(
      await screen.findByText(
        'Para crear la cuenta hay que autorizar el tratamiento de los datos.',
      ),
    ).toBeTruthy();
    expect(repositorio.llamadasRegistrar).toEqual([]);
  });

  it('la casilla de autorización nunca arranca marcada', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    const casilla = screen.getByLabelText(ETIQUETA_AUTORIZACION) as HTMLInputElement;

    expect(casilla.checked).toBe(false);
  });

  it('enlaza la política de tratamiento de datos junto a la casilla', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    const enlace = screen.getByRole('link', { name: 'Leer la política de tratamiento de datos' });

    expect(enlace.getAttribute('href')).toBe('/es/legales/privacidad');
  });

  it('manda la autorización al servidor, no la da por supuesta', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.autorizacionRecibida).toBe(true));
  });

  /**
   * La etiqueta se ve desde el 4 de octubre de 2026. Estuvo escondida (`sr-only`) con el nombre en
   * el placeholder, que desaparece al primer carácter: quien volvía al campo a medio llenar no
   * tenía en pantalla qué estaba escribiendo (WCAG 3.3.2). Se fija que no vuelva a esconderse.
   */
  it.each([['Correo electrónico'], ['Clave'], ['Confirmar clave']])(
    '%s tiene la etiqueta a la vista y no la repite como placeholder',
    async (etiqueta) => {
      await renderPagina(new RepositorioCuentaFalso());

      const campo = await screen.findByLabelText(etiqueta);
      const rotulo = document.querySelector(`label[for="${campo.id}"]`);
      expect(rotulo?.closest('.sr-only')).toBeNull();
      expect(campo.hasAttribute('placeholder')).toBe(false);
    },
  );

  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPagina(new RepositorioCuentaFalso());
    await screen.findByLabelText('Correo electrónico');

    await esperarSinViolaciones(container);
  });

  describe('con Google (ADR-0074)', () => {
    /**
     * Ley 1581: sin la casilla no hay cuenta, tampoco con Google. El botón oficial no se puede
     * deshabilitar, así que se comprueba al volver: no se llama al servidor y se dice qué falta.
     */
    it('sin marcar la autorización no crea la cuenta y dice qué falta', async () => {
      const cuenta = new RepositorioCuentaFalso();
      cuenta.configuracion = GOOGLE_HABILITADO;
      const sesion = new SesionConGoogleFalsa();
      const boton = new BotonGoogleFalso();
      await renderPagina(cuenta, sesion, boton);
      await vi.waitFor(() => expect(boton.pintado).toBe(true));

      boton.entregar('credencial-de-google');

      expect(await screen.findByText(/primero marca la casilla de autorización/)).toBeTruthy();
      expect(sesion.llamadasGoogle).toEqual([]);
    });

    it('con la autorización marcada crea la cuenta y la autorización viaja', async () => {
      const cuenta = new RepositorioCuentaFalso();
      cuenta.configuracion = GOOGLE_HABILITADO;
      const sesion = new SesionConGoogleFalsa();
      const boton = new BotonGoogleFalso();
      const { fixture } = await renderPagina(cuenta, sesion, boton);
      const navegar = vi
        .spyOn(fixture.debugElement.injector.get(Router), 'navigate')
        .mockResolvedValue(true);
      await vi.waitFor(() => expect(boton.pintado).toBe(true));

      fireEvent.click(screen.getByLabelText(ETIQUETA_AUTORIZACION));
      boton.entregar('credencial-de-google');

      await vi.waitFor(() =>
        expect(sesion.llamadasGoogle).toEqual([
          { credencial: 'credencial-de-google', autorizaDatos: true },
        ]),
      );
      expect(cuenta.llamadasRegistrar).toEqual([]);
      // Nace verificada y con la sesión abierta: no hay correo que confirmar, se va a la portada.
      await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['/es']));
    });
  });
});
