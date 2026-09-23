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
import {
  ClaveActualIncorrectaError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from '../../../core/autenticacion/sesion.errores';
import { Sesion } from '../../../core/autenticacion/sesion.model';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import { CambiarClaveAdminPage } from './cambiar-clave-admin.page';

const SESION: Sesion = { usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt.viejo' };
const SESION_NUEVA: Sesion = { usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt.nuevo' };

class RepositorioSesionFalso implements RepositorioSesion {
  claveActualRecibida: string | null = null;
  claveNuevaRecibida: string | null = null;

  constructor(private readonly falla?: Error) {}

  async iniciarSesion(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  async cambiarClave(
    _accessToken: string,
    claveActual: string,
    claveNueva: string,
  ): Promise<Sesion> {
    this.claveActualRecibida = claveActual;
    this.claveNuevaRecibida = claveNueva;
    if (this.falla) {
      throw this.falla;
    }
    return SESION_NUEVA;
  }

  /** La sesión con la que arranca el store: la pantalla solo se abre detrás del guardia. */
  async refrescar(): Promise<Sesion | null> {
    return SESION;
  }

  async cerrarSesion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

async function renderPagina(repositorio: RepositorioSesion) {
  const resultado = await render(CambiarClaveAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin, 'admin/en': enAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [{ provide: REPOSITORIO_SESION, useValue: repositorio }],
  });
  const store = resultado.fixture.debugElement.injector.get(SesionStore);
  await store.listo;
  return { ...resultado, store };
}

function escribir(etiqueta: string, valor: string) {
  fireEvent.input(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

function llenarFormulario(nueva = 'clave-nueva', confirmar = nueva) {
  escribir('Clave actual', 'clave-vieja');
  escribir('Clave nueva', nueva);
  escribir('Repite la clave nueva', confirmar);
}

function enviar() {
  fireEvent.click(screen.getByRole('button', { name: 'Cambiar la clave' }));
}

describe('CambiarClaveAdminPage', () => {
  it('manda las dos claves y avisa que las demás sesiones quedaron cerradas', async () => {
    const repositorio = new RepositorioSesionFalso();
    const { store } = await renderPagina(repositorio);

    llenarFormulario();
    enviar();

    expect(await screen.findByText(/Clave cambiada/)).toBeTruthy();
    expect(repositorio.claveActualRecibida).toBe('clave-vieja');
    expect(repositorio.claveNuevaRecibida).toBe('clave-nueva');
    // La sesión nueva reemplaza a la vieja: el servidor revocó la anterior.
    expect(store.sesion()).toEqual(SESION_NUEVA);
  });

  it('con la clave actual equivocada lo dice, y no es el error genérico', async () => {
    const repositorio = new RepositorioSesionFalso(new ClaveActualIncorrectaError());
    await renderPagina(repositorio);

    llenarFormulario();
    enviar();

    expect(await screen.findByText('La clave actual no es correcta.')).toBeTruthy();
  });

  it('tras demasiados intentos dice que espere, no que falló el servidor', async () => {
    const repositorio = new RepositorioSesionFalso(new DemasiadosIntentosError());
    await renderPagina(repositorio);

    llenarFormulario();
    enviar();

    expect(await screen.findByText(/Demasiados intentos/)).toBeTruthy();
  });

  it('con la sesión vencida no dice que la clave está mal', async () => {
    // Los dos casos llegan como 401 desde el servidor. Decirle "esa no es tu clave" a quien la
    // escribió bien lo pone a buscar un problema que no existe.
    const repositorio = new RepositorioSesionFalso(new SesionExpiradaError());
    await renderPagina(repositorio);

    llenarFormulario();
    enviar();

    expect(await screen.findByText(/sesión venció/)).toBeTruthy();
  });

  it('si las dos claves nuevas no coinciden, no manda nada', async () => {
    const repositorio = new RepositorioSesionFalso();
    await renderPagina(repositorio);

    llenarFormulario('clave-nueva', 'otra-cosa');
    enviar();

    expect(await screen.findByText('Las dos claves no coinciden.')).toBeTruthy();
    expect(repositorio.claveActualRecibida).toBeNull();
  });

  it('el botón de enviar nunca se deshabilita, ni con el formulario vacío', async () => {
    await renderPagina(new RepositorioSesionFalso());

    // Un `disabled` saca al botón del orden de tabulación: quien navega con teclado no lo
    // encuentra y nada le explica por qué no pasa nada (apps/web/CLAUDE.md).
    const boton = screen.getByRole('button', { name: 'Cambiar la clave' });
    expect(boton.hasAttribute('disabled')).toBe(false);
  });
});
