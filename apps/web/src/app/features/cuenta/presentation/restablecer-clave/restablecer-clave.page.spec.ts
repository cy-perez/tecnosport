import { ActivatedRoute } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { RestablecerClavePage } from './restablecer-clave.page';

class RepositorioCuentaFalso implements RepositorioCuenta {
  llamadasRestablecer: { token: string; claveNueva: string }[] = [];

  constructor(private falla = false) {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async registrar(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async verificarCorreo(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async solicitarRecuperacion(): Promise<void> {}

  async restablecerClave(token: string, claveNueva: string): Promise<void> {
    this.llamadasRestablecer.push({ token, claveNueva });
    if (this.falla) {
      throw new Error('token inválido');
    }
  }
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function rutaActivadaFalsa(token: string | null) {
  return { snapshot: { queryParamMap: { get: (clave: string) => (clave === 'token' ? token : null) } } };
}

async function renderPagina(repositorio: RepositorioCuenta, token: string | null) {
  return render(RestablecerClavePage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'cuenta/es': esCuenta, 'cuenta/en': enCuenta } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      { provide: REPOSITORIO_CUENTA, useValue: repositorio },
      { provide: ActivatedRoute, useValue: rutaActivadaFalsa(token) },
    ],
  });
}

async function llenarYEnviar(clave = 'clave-segura', confirmarClave = 'clave-segura') {
  fireEvent.input(screen.getByLabelText('Clave nueva'), { target: { value: clave } });
  fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: confirmarClave } });
  fireEvent.click(screen.getByRole('button', { name: 'Restablecer clave' }));
  await esperar(50);
}

describe('RestablecerClavePage', () => {
  it('sin token en la URL, muestra el error sin mostrar el formulario', async () => {
    await renderPagina(new RepositorioCuentaFalso(), null);

    expect(screen.getByText('Enlace no válido')).toBeTruthy();
    expect(screen.queryByLabelText('Clave nueva')).toBeNull();
  });

  it('el botón arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioCuentaFalso(), 'token-valido');

    expect(screen.getByRole('button', { name: 'Restablecer clave' }).hasAttribute('disabled')).toBe(true);
  });

  it('con un token válido, restablece y muestra el mensaje de éxito', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio, 'token-valido');

    await llenarYEnviar();

    expect(repositorio.llamadasRestablecer).toEqual([{ token: 'token-valido', claveNueva: 'clave-segura' }]);
    expect(screen.getByText('Clave restablecida')).toBeTruthy();
  });

  it('con un token que el servidor rechaza, muestra el error correspondiente', async () => {
    await renderPagina(new RepositorioCuentaFalso(true), 'token-vencido');

    await llenarYEnviar();

    expect(screen.getByText('El enlace no es válido o ya venció. Pide uno nuevo.')).toBeTruthy();
  });

  it('con claves que no coinciden, muestra el error de confirmación', async () => {
    await renderPagina(new RepositorioCuentaFalso(), 'token-valido');

    fireEvent.input(screen.getByLabelText('Clave nueva'), { target: { value: 'clave-segura' } });
    fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: 'otra-clave' } });
    await esperar(10);

    expect(screen.getByText('Las claves no coinciden.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Restablecer clave' }).hasAttribute('disabled')).toBe(true);
  });
});
