import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { RecuperarClavePage } from './recuperar-clave.page';

class RepositorioCuentaFalso implements RepositorioCuenta {
  llamadasSolicitar: string[] = [];

  constructor(private falla = false) {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async registrar(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async verificarCorreo(): Promise<void> {}

  async solicitarRecuperacion(correo: string): Promise<void> {
    this.llamadasSolicitar.push(correo);
    if (this.falla) {
      throw new Error('falló');
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async restablecerClave(): Promise<void> {}
}


async function renderPagina(repositorio: RepositorioCuenta) {
  return render(RecuperarClavePage, {
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

async function llenarYEnviar() {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'cliente@tecnosport.co' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));
}

describe('RecuperarClavePage', () => {
  it('el botón arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioCuentaFalso());

    expect(screen.getByRole('button', { name: 'Enviar enlace' }).hasAttribute('disabled')).toBe(
      true,
    );
  });

  it('al enviar, muestra siempre el mismo mensaje de éxito', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio);

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.llamadasSolicitar).toEqual(['cliente@tecnosport.co']));
    expect(await screen.findByText('Revisa tu correo')).toBeTruthy();
  });

  it('con un error del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioCuentaFalso(true));

    await llenarYEnviar();

    expect(await screen.findByText('No se pudo procesar la solicitud. Intenta de nuevo.')).toBeTruthy();
  });
});
