import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { RegistroClientePage } from './registro-cliente.page';

class RepositorioCuentaFalso implements RepositorioCuenta {
  llamadasRegistrar: { correo: string; clave: string }[] = [];

  constructor(private errorAlRegistrar: 'correo-registrado' | 'generico' | null = null) {}

  async registrar(correo: string, clave: string): Promise<void> {
    this.llamadasRegistrar.push({ correo, clave });
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
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
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
    providers: [{ provide: REPOSITORIO_CUENTA, useValue: repositorio }],
  });
}

async function llenarYEnviar(clave = 'clave-segura', confirmarClave = 'clave-segura') {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: 'cliente@tecnosport.co' } });
  fireEvent.input(screen.getByLabelText('Clave'), { target: { value: clave } });
  fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: confirmarClave } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
  await esperar(50);
}

describe('RegistroClientePage', () => {
  it('el botón crear cuenta arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    expect(screen.getByRole('button', { name: 'Crear cuenta' }).hasAttribute('disabled')).toBe(true);
  });

  it('registrar exitosamente muestra el mensaje de revisa tu correo', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);

    await llenarYEnviar();

    expect(repositorio.llamadasRegistrar).toEqual([
      { correo: 'cliente@tecnosport.co', clave: 'clave-segura' },
    ]);
    expect(screen.getByText('Revisa tu correo')).toBeTruthy();
  });

  it('con un correo ya registrado, muestra ese error específico', async () => {
    await renderPagina(new RepositorioCuentaFalso('correo-registrado'));

    await llenarYEnviar();

    expect(screen.getByText('Ya existe una cuenta con ese correo.')).toBeTruthy();
  });

  it('con un error genérico del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioCuentaFalso('generico'));

    await llenarYEnviar();

    expect(screen.getByText('No se pudo crear la cuenta. Intenta de nuevo.')).toBeTruthy();
  });

  it('con claves que no coinciden, muestra el error de confirmación', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: 'cliente@tecnosport.co' } });
    fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
    fireEvent.input(screen.getByLabelText('Confirmar clave'), { target: { value: 'otra-clave' } });
    await esperar(10);

    expect(screen.getByText('Las claves no coinciden.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Crear cuenta' }).hasAttribute('disabled')).toBe(true);
  });
});
