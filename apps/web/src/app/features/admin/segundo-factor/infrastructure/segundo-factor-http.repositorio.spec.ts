import { TestBed } from '@angular/core/testing';
import {
  REPOSITORIO_SESION,
  RepositorioSesion,
} from '../../../../core/autenticacion/repositorio-sesion.puerto';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from '../../../../core/autenticacion/sesion.errores';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { SegundoFactorHttpRepositorio } from './segundo-factor-http.repositorio';

/**
 * Estos endpoints responden 401 por tres motivos —la clave equivocada, el código equivocado y el
 * token vencido— y la pantalla pide algo distinto con cada uno. Los separa el `codigo` del
 * `ProblemDetail`. Y van con el token puesto a mano, sin el cliente que refresca: un 401 de negocio
 * no puede disparar un refresco ni repetir la petición.
 */
describe('SegundoFactorHttpRepositorio', () => {
  let llamadas: { url: string; init: RequestInit | undefined; peticion: Request }[];

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response, token: string | null = 'jwt.vigente') {
    llamadas = [];
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (peticion: Request, init?: RequestInit) => {
        llamadas.push({ url: peticion.url, init, peticion });
        return respuesta;
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: REPOSITORIO_SESION, useValue: {} as RepositorioSesion },
        SegundoFactorHttpRepositorio,
      ],
    });
    if (token) {
      TestBed.inject(SesionStore).adoptar({
        usuarioId: 'u1',
        rol: 'ADMIN',
        accessToken: token,
        segundoFactorPorActivar: true,
      });
    }
    return TestBed.inject(SegundoFactorHttpRepositorio);
  }

  function json(cuerpo: unknown, estado = 200): Response {
    return new Response(JSON.stringify(cuerpo), {
      status: estado,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  function problema(estado: number, codigo: string): Response {
    return new Response(JSON.stringify({ codigo, status: estado }), {
      status: estado,
      headers: { 'Content-Type': 'application/problem+json' },
    });
  }

  it('consulta con el token de la sesión, y un activadoEn nulo queda nulo', async () => {
    const repositorio = conRespuesta(
      json({ activo: false, activadoEn: null, codigosDeRespaldoRestantes: 0, obligatorio: true }),
    );

    await expect(repositorio.consultar()).resolves.toEqual({
      activo: false,
      activadoEn: null,
      codigosDeRespaldoRestantes: 0,
      obligatorio: true,
    });
    expect(llamadas[0].peticion.headers.get('Authorization')).toBe('Bearer jwt.vigente');
  });

  it('sin sesión no manda nada y lo dice como sesión vencida', async () => {
    const repositorio = conRespuesta(json({}), null);

    await expect(repositorio.consultar()).rejects.toBeInstanceOf(SesionExpiradaError);
    expect(llamadas).toEqual([]);
  });

  it('confirmar devuelve la sesión nueva y los códigos', async () => {
    const repositorio = conRespuesta(
      json({
        sesion: {
          usuarioId: 'u1',
          rol: 'ADMIN',
          accessToken: 'jwt.completo',
          segundoFactorPorActivar: false,
        },
        codigosDeRespaldo: ['AAAA-BBBB-CCCC-DDDD'],
      }),
    );

    await expect(repositorio.confirmarActivacion('123456')).resolves.toEqual({
      sesion: {
        usuarioId: 'u1',
        rol: 'ADMIN',
        accessToken: 'jwt.completo',
        segundoFactorPorActivar: false,
      },
      codigos: ['AAAA-BBBB-CCCC-DDDD'],
    });
  });

  it('401 con CREDENCIALES_INVALIDAS es la clave actual equivocada', async () => {
    await expect(
      conRespuesta(problema(401, 'CREDENCIALES_INVALIDAS')).iniciarActivacion('mala'),
    ).rejects.toBeInstanceOf(ClaveActualIncorrectaError);
  });

  it('401 con CODIGO_SEGUNDO_FACTOR_INVALIDO es el código equivocado', async () => {
    await expect(
      conRespuesta(problema(401, 'CODIGO_SEGUNDO_FACTOR_INVALIDO')).desactivar('clave', '000000'),
    ).rejects.toBeInstanceOf(CodigoSegundoFactorInvalidoError);
  });

  it('401 con NO_AUTENTICADO es la sesión vencida', async () => {
    await expect(
      conRespuesta(problema(401, 'NO_AUTENTICADO')).regenerarCodigos('clave', '123456'),
    ).rejects.toBeInstanceOf(SesionExpiradaError);
  });

  it('429 es el límite de intentos', async () => {
    await expect(
      conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO')).confirmarActivacion('123456'),
    ).rejects.toBeInstanceOf(DemasiadosIntentosError);
  });

  it('un 409 de estado llega como ErrorHttp con su código, para traducirlo', async () => {
    const error = await conRespuesta(problema(409, 'SEGUNDO_FACTOR_OBLIGATORIO'))
      .desactivar('clave', '123456')
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ErrorHttp);
    expect((error as ErrorHttp).codigo).toBe('SEGUNDO_FACTOR_OBLIGATORIO');
  });
});
