import { ActivatedRoute } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import enCuenta from '../../../../../assets/i18n/scopes/cuenta/en.json';
import esCuenta from '../../../../../assets/i18n/scopes/cuenta/es.json';
import { REPOSITORIO_CUENTA, RepositorioCuenta } from '../../domain/repositorio-cuenta.puerto';
import { VerificarCorreoPage } from './verificar-correo.page';

class RepositorioCuentaFalso implements RepositorioCuenta {
  llamadasVerificar: string[] = [];

  constructor(private falla = false) {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async registrar(): Promise<void> {}

  async verificarCorreo(token: string): Promise<void> {
    this.llamadasVerificar.push(token);
    if (this.falla) {
      throw new Error('token inválido');
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async solicitarRecuperacion(): Promise<void> {}

  // eslint-disable-next-line @typescript-eslint/no-empty-function -- no usado en estas pruebas
  async restablecerClave(): Promise<void> {}
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function rutaActivadaFalsa(token: string | null) {
  return { snapshot: { queryParamMap: { get: (clave: string) => (clave === 'token' ? token : null) } } };
}

async function renderPagina(repositorio: RepositorioCuenta, token: string | null) {
  return render(VerificarCorreoPage, {
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

describe('VerificarCorreoPage', () => {
  it('con un token válido, verifica y muestra el mensaje de éxito', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio, 'token-valido');

    await esperar(50);

    expect(repositorio.llamadasVerificar).toEqual(['token-valido']);
    expect(screen.getByText('Correo verificado')).toBeTruthy();
  });

  it('con un token que el servidor rechaza, muestra el mensaje de error', async () => {
    await renderPagina(new RepositorioCuentaFalso(true), 'token-vencido');

    await esperar(50);

    expect(screen.getByText('Enlace no válido')).toBeTruthy();
  });

  it('sin token en la URL, muestra el mensaje de error sin llamar al repositorio', async () => {
    const repositorio = new RepositorioCuentaFalso();
    await renderPagina(repositorio, null);

    await esperar(50);

    expect(repositorio.llamadasVerificar).toEqual([]);
    expect(screen.getByText('Enlace no válido')).toBeTruthy();
  });
});
