import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { RegistroClientePage } from './registro-cliente.page';

class RepositorioCuentaFalso implements RepositorioCuenta {
  llamadasRegistrar: { correo: string; clave: string; autorizaDatos: boolean }[] = [];

  constructor(private errorAlRegistrar: 'correo-registrado' | 'generico' | null = null) {}

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

async function renderPagina(repositorio: RepositorioCuenta) {
  return render(RegistroClientePage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'cuenta/es': esCuenta, 'cuenta/en': enCuenta } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([]), { provide: REPOSITORIO_CUENTA, useValue: repositorio }],
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
   * Los campos dejaron de enseñar su etiqueta el 24 de septiembre de 2026: el nombre vive en el
   * placeholder y la etiqueta se fue a `sr-only`. Lo que hay que vigilar es justo el filo de esa
   * decisión — que la etiqueta siga existiendo para quien no la ve, y que el placeholder de verdad
   * esté puesto, porque sin él el campo se queda sin nada legible en cuanto se esconde la
   * etiqueta—. La prueba de axe de más abajo cubre lo demás.
   */
  it.each([['Correo electrónico'], ['Clave'], ['Confirmar clave']])(
    '%s conserva su etiqueta y lleva placeholder',
    async (etiqueta) => {
      await renderPagina(new RepositorioCuentaFalso());

      const campo = await screen.findByLabelText(etiqueta);
      expect(campo.getAttribute('placeholder')).toBe(etiqueta);
    },
  );

  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderPagina(new RepositorioCuentaFalso());
    await screen.findByLabelText('Correo electrónico');

    await esperarSinViolaciones(container);
  });
});
