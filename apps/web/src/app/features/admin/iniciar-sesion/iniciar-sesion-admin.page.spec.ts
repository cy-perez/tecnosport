import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import enAdmin from '../../../../assets/i18n/scopes/admin/en.json';
import esAdmin from '../../../../assets/i18n/scopes/admin/es.json';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../core/autenticacion/repositorio-sesion.puerto';
import { ErrorHttp } from '../../../core/http/respuesta-http';
import {
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  DesafioSegundoFactorVencidoError,
} from '../../../core/autenticacion/sesion.errores';
import { ResultadoInicioSesion, Sesion } from '../../../core/autenticacion/sesion.model';
import { IniciarSesionAdminPage } from './iniciar-sesion-admin.page';

class RepositorioSesionFalso implements RepositorioSesion {
  async iniciarSesionConGoogle(): Promise<Sesion> {
    throw new Error('no usado en esta prueba');
  }

  llamadasCerrar = 0;

  constructor(
    private sesionAlIniciar:
      | Sesion
      | { error: true }
      | { falloServidor: true }
      | { limitado: true }
      | { desafio: string } = {
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt',
      segundoFactorPorActivar: false,
    },
  ) {}

  async iniciarSesion(): Promise<ResultadoInicioSesion> {
    // El adaptador real lanza `ErrorHttp` con su código (`sesion-http.repositorio.ts`); el
    // doble imita esa forma para que la pantalla se pruebe contra el fallo de verdad.
    if ('error' in this.sesionAlIniciar) {
      throw new ErrorHttp(401, 'no se pudo iniciar sesión');
    }
    if ('limitado' in this.sesionAlIniciar) {
      throw new DemasiadosIntentosError();
    }
    if ('falloServidor' in this.sesionAlIniciar) {
      throw new ErrorHttp(500, 'no se pudo iniciar sesión');
    }
    if ('desafio' in this.sesionAlIniciar) {
      return {
        tipo: 'segundo-factor',
        desafio: this.sesionAlIniciar.desafio,
        venceEn: '2026-10-27T12:05:00Z',
      };
    }
    return { tipo: 'sesion', sesion: this.sesionAlIniciar };
  }

  /** Lo que responde el segundo paso: la sesión, o el error que lanza el adaptador real. */
  alVerificar: Sesion | Error = {
    usuarioId: 'u1',
    rol: 'ADMIN',
    accessToken: 'jwt',
    segundoFactorPorActivar: false,
  };
  verificaciones: { desafio: string; codigo: string }[] = [];

  async verificarSegundoFactor(desafio: string, codigo: string): Promise<Sesion> {
    this.verificaciones.push({ desafio, codigo });
    if (this.alVerificar instanceof Error) {
      throw this.alVerificar;
    }
    return this.alVerificar;
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

async function renderPagina(
  repositorio: RepositorioSesion,
  destino?: string,
  otros: Record<string, string> = {},
) {
  return render(IniciarSesionAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin, 'admin/en': enAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      { provide: REPOSITORIO_SESION, useValue: repositorio },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            queryParamMap: convertToParamMap(destino === undefined ? otros : { ...otros, destino }),
          },
        },
      },
    ],
  });
}

async function llenarYEnviar() {
  fireEvent.input(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'admin@tecnosport.co' },
  });
  fireEvent.input(screen.getByLabelText('Clave'), { target: { value: 'clave-segura' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('IniciarSesionAdminPage', () => {
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

    expect(await screen.findByText('Escribe el correo.')).toBeTruthy();
  });

  /** El panel salió sin que el servidor confirmara el cierre: la cookie de refresco sigue viva. */
  it('avisa cuando el panel no pudo confirmar el cierre de sesión con el servidor', async () => {
    await renderPagina(new RepositorioSesionFalso(), undefined, { cierre: 'sin-confirmar' });

    expect(screen.getByText(esAdmin.iniciarSesion.cierre_sin_confirmar)).toBeTruthy();
  });

  it('sin ese aviso en la URL no dice nada del cierre', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.queryByText(esAdmin.iniciarSesion.cierre_sin_confirmar)).toBeNull();
  });

  /**
   * `transloco.translate()` dentro de un `computed` no lee ninguna señal: el error se quedaba en el
   * idioma en que se pintó por primera vez aunque el visitante cambiara de idioma.
   */
  it('el error de un campo cambia de idioma con el sitio', async () => {
    const { fixture } = await renderPagina(new RepositorioSesionFalso());
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByText(esAdmin.iniciarSesion.errores.correo_requerido)).toBeTruthy();

    fixture.debugElement.injector.get(TranslocoService).setActiveLang('en');

    expect(await screen.findByText(enAdmin.iniciarSesion.errores.correo_requerido)).toBeTruthy();
    expect(screen.queryByText(esAdmin.iniciarSesion.errores.correo_requerido)).toBeNull();
  });

  it('con credenciales válidas de ADMIN, navega al panel', async () => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({
        usuarioId: 'u1',
        rol: 'ADMIN',
        accessToken: 'jwt',
        segundoFactorPorActivar: false,
      }),
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
  });

  it('vuelve al destino que puso el guardia, en vez de al panel', async () => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({
        usuarioId: 'u1',
        rol: 'ADMIN',
        accessToken: 'jwt',
        segundoFactorPorActivar: false,
      }),
      '/es/admin/productos/abc-123/captura-360',
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() =>
      expect(navegar).toHaveBeenCalledWith('/es/admin/productos/abc-123/captura-360'),
    );
  });

  it.each([
    ['https://sitio-ajeno.example/roba', 'una URL absoluta a otro sitio'],
    ['//sitio-ajeno.example/roba', 'una URL sin esquema, que el navegador resuelve a otro sitio'],
    ['/es/carrito', 'una ruta de este sitio pero fuera de /admin'],
  ])('ignora %s (%s) y va al panel', async (destino) => {
    const { fixture } = await renderPagina(
      new RepositorioSesionFalso({
        usuarioId: 'u1',
        rol: 'ADMIN',
        accessToken: 'jwt',
        segundoFactorPorActivar: false,
      }),
      destino,
    );
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
  });

  it('con una cuenta que no es ADMIN, cierra la sesión y muestra el error', async () => {
    const repositorio = new RepositorioSesionFalso({
      usuarioId: 'u1',
      rol: 'CLIENTE',
      accessToken: 'jwt',
      segundoFactorPorActivar: false,
    });
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    await llenarYEnviar();

    await vi.waitFor(() => expect(repositorio.llamadasCerrar).toBe(1));
    expect(await screen.findByText('Esta cuenta no tiene acceso al panel.')).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con credenciales incorrectas, muestra un error genérico', async () => {
    await renderPagina(new RepositorioSesionFalso({ error: true }));

    await llenarYEnviar();

    expect(await screen.findByText('Correo o clave incorrectos.')).toBeTruthy();
  });

  it('con el límite de intentos agotado dice que espere, no que la clave está mal', async () => {
    // Esto pasó de verdad el 22 de septiembre de 2026 probando el cambio de clave del panel:
    // entrar, salir y volver a entrar agota los cinco intentos por cuenta —el limitador cuenta
    // también los exitosos, porque pide permiso antes de verificar— y la pantalla respondía
    // "Correo o clave incorrectos." con la clave perfectamente bien.
    await renderPagina(new RepositorioSesionFalso({ limitado: true }));

    await llenarYEnviar();

    expect(await screen.findByText(/Demasiados intentos/)).toBeTruthy();
    expect(screen.queryByText('Correo o clave incorrectos.')).toBeNull();
  });

  it('el correo y la clave se declaran al gestor de contraseñas', async () => {
    await renderPagina(new RepositorioSesionFalso());

    expect(screen.getByLabelText('Correo electrónico').getAttribute('autocomplete')).toBe(
      'username',
    );
    expect(screen.getByLabelText('Clave').getAttribute('autocomplete')).toBe('current-password');
  });
});

/**
 * ADR-0096: con el segundo factor activo, la clave correcta no abre sesión; responde un desafío y
 * la pantalla pide el código. Lo que importa de cada caso es a qué paso deja a la persona: un
 * código mal escrito se corrige ahí mismo, un desafío vencido manda de vuelta a la clave.
 */
describe('IniciarSesionAdminPage con segundo factor', () => {
  const textos = esAdmin.iniciarSesion.segundoFactor;

  async function hastaElCodigo(repositorio = new RepositorioSesionFalso({ desafio: 'desafio-1' })) {
    const vista = await renderPagina(repositorio);
    const navegar = vi.spyOn(vista.fixture.debugElement.injector.get(Router), 'navigateByUrl');
    await llenarYEnviar();
    await screen.findByLabelText(textos.codigo_aplicacion);
    return { repositorio, navegar };
  }

  function escribirCodigoYVerificar(codigo: string, etiqueta = textos.codigo_aplicacion) {
    fireEvent.input(screen.getByLabelText(etiqueta), { target: { value: codigo } });
    fireEvent.click(screen.getByRole('button', { name: textos.verificar }));
  }

  it('la clave correcta pide el código y todavía no entra', async () => {
    const { navegar } = await hastaElCodigo();

    expect(screen.getByText(textos.explicacion_aplicacion)).toBeTruthy();
    expect(screen.queryByLabelText('Clave')).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('el campo del código abre el teclado numérico y se ofrece al autocompletado', async () => {
    await hastaElCodigo();

    const campo = screen.getByLabelText(textos.codigo_aplicacion);
    expect(campo.getAttribute('autocomplete')).toBe('one-time-code');
    expect(campo.getAttribute('inputmode')).toBe('numeric');
  });

  it('con el código bueno entra al panel, presentando el desafío que llegó', async () => {
    const { repositorio, navegar } = await hastaElCodigo();

    escribirCodigoYVerificar('123 456');

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
    // Los espacios se quitan aquí: quien copia "123 456" de la aplicación no se equivocó.
    expect(repositorio.verificaciones).toEqual([{ desafio: 'desafio-1', codigo: '123456' }]);
  });

  it('un código con forma imposible no llega al servidor', async () => {
    const { repositorio } = await hastaElCodigo();

    escribirCodigoYVerificar('12345');

    expect(await screen.findByText(textos.errores.codigo_formato)).toBeTruthy();
    expect(repositorio.verificaciones).toEqual([]);
  });

  it('con el código equivocado se queda en el código y lo dice', async () => {
    const repositorio = new RepositorioSesionFalso({ desafio: 'desafio-1' });
    repositorio.alVerificar = new CodigoSegundoFactorInvalidoError();
    const { navegar } = await hastaElCodigo(repositorio);

    escribirCodigoYVerificar('000000');

    expect(await screen.findByText(textos.error_codigo)).toBeTruthy();
    expect(screen.getByLabelText(textos.codigo_aplicacion)).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con el desafío vencido vuelve a la clave y explica por qué', async () => {
    const repositorio = new RepositorioSesionFalso({ desafio: 'desafio-1' });
    repositorio.alVerificar = new DesafioSegundoFactorVencidoError();
    await hastaElCodigo(repositorio);

    escribirCodigoYVerificar('123456');

    expect(await screen.findByText(textos.error_desafio_vencido)).toBeTruthy();
    expect(screen.getByLabelText('Clave')).toBeTruthy();
    expect(screen.queryByLabelText(textos.codigo_aplicacion)).toBeNull();
  });

  it('el límite de intentos dice que espere, no que el código está mal', async () => {
    const repositorio = new RepositorioSesionFalso({ desafio: 'desafio-1' });
    repositorio.alVerificar = new DemasiadosIntentosError();
    await hastaElCodigo(repositorio);

    escribirCodigoYVerificar('123456');

    expect(await screen.findByText(textos.error_demasiados_intentos)).toBeTruthy();
    expect(screen.queryByText(textos.error_codigo)).toBeNull();
  });

  it('acepta un código de respaldo con guiones y sin el teclado numérico', async () => {
    const { repositorio, navegar } = await hastaElCodigo();

    fireEvent.click(screen.getByRole('button', { name: textos.usar_respaldo }));
    const campo = await screen.findByLabelText(textos.codigo_respaldo);
    expect(campo.getAttribute('inputmode')).toBe('text');
    escribirCodigoYVerificar('abcd-efgh-ijkl-mnop', textos.codigo_respaldo);

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/es/admin/panel'));
    expect(repositorio.verificaciones).toEqual([
      { desafio: 'desafio-1', codigo: 'abcdefghijklmnop' },
    ]);
  });

  it('volver lleva a la clave y suelta el desafío', async () => {
    await hastaElCodigo();

    fireEvent.click(screen.getByRole('button', { name: textos.volver }));

    expect(await screen.findByLabelText('Clave')).toBeTruthy();
    expect(screen.queryByLabelText(textos.codigo_aplicacion)).toBeNull();
  });
});
