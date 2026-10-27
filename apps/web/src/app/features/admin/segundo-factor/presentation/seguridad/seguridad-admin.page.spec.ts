import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../../core/autenticacion/repositorio-sesion.puerto';
import { CodigoSegundoFactorInvalidoError } from '../../../../../core/autenticacion/sesion.errores';
import { Sesion } from '../../../../../core/autenticacion/sesion.model';
import { SesionStore } from '../../../../../core/autenticacion/sesion.store';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import {
  REPOSITORIO_SEGUNDO_FACTOR,
  RepositorioSegundoFactor,
} from '../../domain/repositorio-segundo-factor.puerto';
import {
  ActivacionIniciada,
  EstadoSegundoFactor,
  SesionConCodigos,
} from '../../domain/segundo-factor.model';
import { SeguridadAdminPage } from './seguridad-admin.page';

const textos = esAdmin.segundoFactor;

const SESION: Sesion = {
  usuarioId: 'u1',
  rol: 'ADMIN',
  accessToken: 'jwt.viejo',
  segundoFactorPorActivar: false,
};
const SESION_NUEVA: Sesion = { ...SESION, accessToken: 'jwt.nuevo' };

const ACTIVO: EstadoSegundoFactor = {
  activo: true,
  activadoEn: '2026-10-27T15:00:00Z',
  codigosDeRespaldoRestantes: 7,
  obligatorio: false,
};

class RepositorioSegundoFactorFalso implements RepositorioSegundoFactor {
  constructor(public estado: EstadoSegundoFactor = ACTIVO) {}

  alRegenerar: SesionConCodigos | Error = {
    sesion: SESION_NUEVA,
    codigos: ['QQQQ-RRRR-SSSS-TTTT'],
  };
  alDesactivar: Sesion | Error = SESION_NUEVA;
  readonly regeneraciones: { claveActual: string; codigo: string }[] = [];
  readonly desactivaciones: { claveActual: string; codigo: string }[] = [];

  async consultar(): Promise<EstadoSegundoFactor> {
    return this.estado;
  }

  async iniciarActivacion(): Promise<ActivacionIniciada> {
    throw new Error('no usado en esta prueba');
  }

  async confirmarActivacion(): Promise<SesionConCodigos> {
    throw new Error('no usado en esta prueba');
  }

  async regenerarCodigos(claveActual: string, codigo: string): Promise<SesionConCodigos> {
    this.regeneraciones.push({ claveActual, codigo });
    if (this.alRegenerar instanceof Error) {
      throw this.alRegenerar;
    }
    return this.alRegenerar;
  }

  async desactivar(claveActual: string, codigo: string): Promise<Sesion> {
    this.desactivaciones.push({ claveActual, codigo });
    if (this.alDesactivar instanceof Error) {
      throw this.alDesactivar;
    }
    this.estado = {
      activo: false,
      activadoEn: null,
      codigosDeRespaldoRestantes: 0,
      obligatorio: false,
    };
    return this.alDesactivar;
  }
}

async function renderPagina(repositorio = new RepositorioSegundoFactorFalso()) {
  const vista = await render(SeguridadAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_SESION, useValue: {} as RepositorioSesion },
      { provide: REPOSITORIO_SEGUNDO_FACTOR, useValue: repositorio },
    ],
  });
  const sesionStore = vista.fixture.debugElement.injector.get(SesionStore);
  sesionStore.adoptar(SESION);
  return { ...vista, repositorio, sesionStore };
}

function seccion(titulo: string): HTMLElement {
  return screen.getByRole('region', { name: titulo });
}

function llenar(region: HTMLElement, clave: string, codigo: string) {
  const campos = region.querySelectorAll('input');
  fireEvent.input(campos[0], { target: { value: clave } });
  fireEvent.input(campos[1], { target: { value: codigo } });
}

describe('SeguridadAdminPage', () => {
  it('sin el factor activo, ofrece activarlo', async () => {
    await renderPagina(
      new RepositorioSegundoFactorFalso({
        activo: false,
        activadoEn: null,
        codigosDeRespaldoRestantes: 0,
        obligatorio: false,
      }),
    );

    expect(await screen.findByText(textos.seguridad.inactivo)).toBeTruthy();
    const enlace = screen.getByRole('link', { name: textos.seguridad.activar });
    expect(enlace.getAttribute('href')).toBe('/es/admin/activar-segundo-factor');
  });

  it('activo, dice desde cuándo y cuántos códigos quedan', async () => {
    await renderPagina();

    expect(
      await screen.findByText('Segundo factor activo desde el 27 de octubre de 2026.'),
    ).toBeTruthy();
    expect(screen.getByText('Te quedan 7 código(s) de respaldo sin usar.')).toBeTruthy();
  });

  it('regenerar manda la clave y el código normalizado, adopta la sesión y muestra los códigos', async () => {
    const { repositorio, sesionStore } = await renderPagina();
    await screen.findByText(textos.seguridad.regenerar_titulo);

    const region = seccion(textos.seguridad.regenerar_titulo);
    llenar(region, 'clave-segura', 'abcd-efgh-ijkl-mnop');
    fireEvent.click(screen.getByRole('button', { name: textos.seguridad.regenerar }));

    expect(await screen.findByText('QQQQ-RRRR-SSSS-TTTT')).toBeTruthy();
    expect(repositorio.regeneraciones).toEqual([
      { claveActual: 'clave-segura', codigo: 'abcdefghijklmnop' },
    ]);
    expect(sesionStore.sesion()).toEqual(SESION_NUEVA);
  });

  it('con el código equivocado no regenera y lo dice en su sección', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alRegenerar = new CodigoSegundoFactorInvalidoError();
    const { sesionStore } = await renderPagina(repositorio);
    await screen.findByText(textos.seguridad.regenerar_titulo);

    llenar(seccion(textos.seguridad.regenerar_titulo), 'clave-segura', '000000');
    fireEvent.click(screen.getByRole('button', { name: textos.seguridad.regenerar }));

    expect(await screen.findByText(textos.error_codigo)).toBeTruthy();
    expect(sesionStore.sesion()).toEqual(SESION);
  });

  it('obligatorio, no ofrece desactivarlo', async () => {
    await renderPagina(new RepositorioSegundoFactorFalso({ ...ACTIVO, obligatorio: true }));

    expect(await screen.findByText(textos.seguridad.obligatorio)).toBeTruthy();
    expect(screen.queryByRole('button', { name: textos.seguridad.desactivar })).toBeNull();
  });

  it('desactivar adopta la sesión nueva y lo confirma', async () => {
    const { repositorio, sesionStore } = await renderPagina();
    await screen.findByText(textos.seguridad.desactivar_titulo);

    llenar(seccion(textos.seguridad.desactivar_titulo), 'clave-segura', '123 456');
    fireEvent.click(screen.getByRole('button', { name: textos.seguridad.desactivar }));

    expect(await screen.findByText(textos.seguridad.desactivado)).toBeTruthy();
    expect(repositorio.desactivaciones).toEqual([
      { claveActual: 'clave-segura', codigo: '123456' },
    ]);
    expect(sesionStore.sesion()).toEqual(SESION_NUEVA);
    expect(await screen.findByText(textos.seguridad.inactivo)).toBeTruthy();
  });

  /** El servidor sí puede responder el 409 aunque la pantalla no lo ofrezca: la bandera cambió. */
  it('el 409 de obligatorio se traduce desde admin.errores', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alDesactivar = new ErrorHttp(409, 'obligatorio', 'SEGUNDO_FACTOR_OBLIGATORIO');
    await renderPagina(repositorio);
    await screen.findByText(textos.seguridad.desactivar_titulo);

    llenar(seccion(textos.seguridad.desactivar_titulo), 'clave-segura', '123456');
    fireEvent.click(screen.getByRole('button', { name: textos.seguridad.desactivar }));

    expect(await screen.findByText(esAdmin.errores.segundo_factor_obligatorio)).toBeTruthy();
  });

  it('sin código no envía y dice qué falta', async () => {
    const { repositorio } = await renderPagina();
    await screen.findByText(textos.seguridad.desactivar_titulo);

    llenar(seccion(textos.seguridad.desactivar_titulo), 'clave-segura', '');
    fireEvent.click(screen.getByRole('button', { name: textos.seguridad.desactivar }));

    expect(await screen.findByText(textos.errores.codigo_requerido)).toBeTruthy();
    expect(repositorio.desactivaciones).toEqual([]);
  });
});
