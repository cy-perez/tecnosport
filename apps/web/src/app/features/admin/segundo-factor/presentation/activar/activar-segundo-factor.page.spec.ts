import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../../core/autenticacion/repositorio-sesion.puerto';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
} from '../../../../../core/autenticacion/sesion.errores';
import { Sesion } from '../../../../../core/autenticacion/sesion.model';
import { SesionStore } from '../../../../../core/autenticacion/sesion.store';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { PORTAPAPELES } from '../../../../../core/portapapeles/portapapeles';
import {
  REPOSITORIO_SEGUNDO_FACTOR,
  RepositorioSegundoFactor,
} from '../../domain/repositorio-segundo-factor.puerto';
import {
  ActivacionIniciada,
  EstadoSegundoFactor,
  SesionConCodigos,
} from '../../domain/segundo-factor.model';
import { ActivarSegundoFactorPage } from './activar-segundo-factor.page';

const textos = esAdmin.segundoFactor;

const SESION_RESTRINGIDA: Sesion = {
  usuarioId: 'u1',
  rol: 'ADMIN',
  accessToken: 'jwt.restringido',
  segundoFactorPorActivar: true,
};
const SESION_COMPLETA: Sesion = {
  ...SESION_RESTRINGIDA,
  accessToken: 'jwt.completo',
  segundoFactorPorActivar: false,
};
const CODIGOS = Array.from({ length: 10 }, (_, i) => `AAAA-BBBB-CCCC-DDD${'ABCDEFGHIJ'[i]}`);

/** Doble escrito a mano (docs/06-testing.md). */
class RepositorioSegundoFactorFalso implements RepositorioSegundoFactor {
  alIniciar: ActivacionIniciada | Error = {
    secreto: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
    uri: 'otpauth://totp/TecnoSport:admin%40tecnosport.co?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
    digitos: 6,
    periodoSegundos: 30,
  };
  alConfirmar: SesionConCodigos | Error = { sesion: SESION_COMPLETA, codigos: CODIGOS };
  readonly clavesRecibidas: string[] = [];
  readonly codigosRecibidos: string[] = [];

  async consultar(): Promise<EstadoSegundoFactor> {
    throw new Error('no usado en esta prueba');
  }

  async iniciarActivacion(claveActual: string): Promise<ActivacionIniciada> {
    this.clavesRecibidas.push(claveActual);
    if (this.alIniciar instanceof Error) {
      throw this.alIniciar;
    }
    return this.alIniciar;
  }

  async confirmarActivacion(codigo: string): Promise<SesionConCodigos> {
    this.codigosRecibidos.push(codigo);
    if (this.alConfirmar instanceof Error) {
      throw this.alConfirmar;
    }
    return this.alConfirmar;
  }

  async regenerarCodigos(): Promise<SesionConCodigos> {
    throw new Error('no usado en esta prueba');
  }

  async desactivar(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }
}

async function renderPagina(
  repositorio = new RepositorioSegundoFactorFalso(),
  sesion: Sesion = SESION_RESTRINGIDA,
) {
  const copiados: string[] = [];
  const vista = await render(ActivarSegundoFactorPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_SESION, useValue: {} as RepositorioSesion },
      { provide: REPOSITORIO_SEGUNDO_FACTOR, useValue: repositorio },
      {
        provide: PORTAPAPELES,
        useValue: { copiar: async (texto: string) => void copiados.push(texto) },
      },
    ],
  });
  const sesionStore = vista.fixture.debugElement.injector.get(SesionStore);
  sesionStore.adoptar(sesion);
  vista.fixture.detectChanges();
  const navegar = vi.spyOn(vista.fixture.debugElement.injector.get(Router), 'navigateByUrl');
  return { ...vista, repositorio, sesionStore, navegar, copiados };
}

async function escribirClaveYContinuar(clave = 'clave-segura') {
  fireEvent.input(screen.getByLabelText(textos.claveActual), { target: { value: clave } });
  fireEvent.click(screen.getByRole('button', { name: textos.activar.continuar }));
}

async function hastaLosCodigos() {
  const vista = await renderPagina();
  await escribirClaveYContinuar();
  await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
  fireEvent.input(screen.getByLabelText(textos.codigoAplicacion), {
    target: { value: '123 456' },
  });
  fireEvent.click(screen.getByRole('button', { name: textos.activar.activar }));
  await screen.findByText(textos.codigos.aviso);
  return vista;
}

describe('ActivarSegundoFactorPage', () => {
  it('con la sesión restringida dice que activarlo no es opcional', async () => {
    await renderPagina();

    expect(screen.getByText(textos.activar.explicacion_obligatoria)).toBeTruthy();
  });

  it('con la sesión completa lo presenta como una opción', async () => {
    await renderPagina(new RepositorioSegundoFactorFalso(), SESION_COMPLETA);

    expect(screen.getByText(textos.activar.explicacion)).toBeTruthy();
  });

  it('con la clave buena muestra la clave TOTP agrupada y la URI como texto', async () => {
    const { repositorio } = await renderPagina();

    await escribirClaveYContinuar();

    expect(await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeTruthy();
    expect(screen.getByText(/^otpauth:\/\/totp\//)).toBeTruthy();
    expect(repositorio.clavesRecibidas).toEqual(['clave-segura']);
  });

  it('copia la clave sin los espacios con los que se muestra', async () => {
    const { copiados } = await renderPagina();
    await escribirClaveYContinuar();
    await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');

    fireEvent.click(screen.getByRole('button', { name: textos.activar.copiar_clave }));

    expect(await screen.findByText(textos.activar.copiado)).toBeTruthy();
    expect(copiados).toEqual(['JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP']);
  });

  it('con la clave equivocada se queda en la clave y lo dice', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alIniciar = new ClaveActualIncorrectaError();
    await renderPagina(repositorio);

    await escribirClaveYContinuar('mala');

    expect(await screen.findByText(textos.error_clave_actual)).toBeTruthy();
    expect(screen.getByLabelText(textos.claveActual)).toBeTruthy();
  });

  it('el 409 de "ya activo" se traduce desde admin.errores', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alIniciar = new ErrorHttp(409, 'ya activo', 'SEGUNDO_FACTOR_YA_ACTIVO');
    await renderPagina(repositorio, SESION_COMPLETA);

    await escribirClaveYContinuar();

    expect(await screen.findByText(esAdmin.errores.segundo_factor_ya_activo)).toBeTruthy();
  });

  it('un código de respaldo no confirma la activación: pide los seis dígitos', async () => {
    const { repositorio } = await renderPagina();
    await escribirClaveYContinuar();
    await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');

    fireEvent.input(screen.getByLabelText(textos.codigoAplicacion), {
      target: { value: 'ABCD-EFGH-IJKL-MNOP' },
    });
    fireEvent.click(screen.getByRole('button', { name: textos.activar.activar }));

    expect(await screen.findByText(textos.errores.codigo_seis_digitos)).toBeTruthy();
    expect(repositorio.codigosRecibidos).toEqual([]);
  });

  it('con el código equivocado se queda en el código', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alConfirmar = new CodigoSegundoFactorInvalidoError();
    await renderPagina(repositorio);
    await escribirClaveYContinuar();
    await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');

    fireEvent.input(screen.getByLabelText(textos.codigoAplicacion), {
      target: { value: '000000' },
    });
    fireEvent.click(screen.getByRole('button', { name: textos.activar.activar }));

    expect(await screen.findByText(textos.error_codigo)).toBeTruthy();
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeTruthy();
  });

  it('si la activación pendiente venció, vuelve a pedir la clave', async () => {
    const repositorio = new RepositorioSegundoFactorFalso();
    repositorio.alConfirmar = new ErrorHttp(
      409,
      'no iniciada',
      'ACTIVACION_SEGUNDO_FACTOR_NO_INICIADA',
    );
    await renderPagina(repositorio);
    await escribirClaveYContinuar();
    await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');

    fireEvent.input(screen.getByLabelText(textos.codigoAplicacion), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: textos.activar.activar }));

    expect(
      await screen.findByText(esAdmin.errores.activacion_segundo_factor_no_iniciada),
    ).toBeTruthy();
    expect(screen.getByLabelText(textos.claveActual)).toBeTruthy();
  });

  /**
   * El servidor revocó la sesión restringida y abrió una completa. Sin adoptarla, "Ir al panel"
   * volvería a chocar con el guardia —el token sigue diciendo "por activar"— y la pestaña seguiría
   * con un token que el servidor ya no reconoce.
   */
  it('al activar adopta la sesión completa y muestra los diez códigos una vez', async () => {
    const { repositorio, sesionStore } = await hastaLosCodigos();

    expect(repositorio.codigosRecibidos).toEqual(['123456']);
    expect(sesionStore.sesion()).toEqual(SESION_COMPLETA);
    for (const codigo of CODIGOS) {
      expect(screen.getByText(codigo)).toBeTruthy();
    }
    // La clave TOTP ya no está en pantalla: ya cumplió.
    expect(screen.queryByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeNull();
  });

  it('los códigos se descargan como texto, uno por línea', async () => {
    await hastaLosCodigos();

    const enlace = screen.getByRole('link', { name: textos.codigos.descargar });
    expect(enlace.getAttribute('download')).toBe(textos.codigos.archivo);
    expect(decodeURIComponent(enlace.getAttribute('href') ?? '')).toBe(
      'data:text/plain;charset=utf-8,' + CODIGOS.join('\n') + '\n',
    );
  });

  it('no entra al panel sin marcar que guardó los códigos, y lo dice', async () => {
    const { navegar } = await hastaLosCodigos();

    fireEvent.click(screen.getByRole('button', { name: textos.activar.ir_al_panel }));

    expect(await screen.findByText(textos.codigos.error_sin_guardar)).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con la casilla marcada entra al panel', async () => {
    const { navegar } = await hastaLosCodigos();

    fireEvent.click(screen.getByLabelText(textos.codigos.guardados));
    fireEvent.click(screen.getByRole('button', { name: textos.activar.ir_al_panel }));

    expect(navegar).toHaveBeenCalledWith('/es/admin/panel');
  });

  it('la pantalla de los códigos no tiene violaciones de accesibilidad', async () => {
    const { container } = await hastaLosCodigos();

    await esperarSinViolaciones(container);
  });
});
