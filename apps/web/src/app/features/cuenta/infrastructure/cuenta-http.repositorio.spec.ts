import { DemasiadosIntentosError } from '../../../core/autenticacion/sesion.errores';
import { ErrorHttp } from '../../../core/http/respuesta-http';
import { CorreoYaRegistradoError } from '../domain/cuenta.errores';
import { CuentaHttpRepositorio } from './cuenta-http.repositorio';

/**
 * Esta clase se prueba **contra `fetch`** y no contra un doble, y ese es todo el motivo de que
 * exista.
 *
 * Las pantallas de esta funcionalidad deciden a quién atribuir un fallo con `esFalloDelServidor`,
 * que cuenta como fallo del servidor todo lo que **no** sea un `ErrorHttp` de 4xx. Mientras este
 * adaptador lanzó `Error` a secas, esa función respondía `true` siempre y las ramas de 4xx de
 * `RestablecerClavePage` y `VerificarCorreoPage` eran código muerto: un enlace vencido se anunciaba
 * como "no pudimos conectarnos con el servidor".
 *
 * Las pruebas de esas dos pantallas pasaban en verde **ejercitando una rama que nadie alcanzaba**,
 * porque sus dobles sí lanzaban `ErrorHttp`. El doble era más correcto que el código real, así que
 * la única forma de notarlo era probar el código real. Eso es lo que hay aquí.
 */
describe('CuentaHttpRepositorio', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response): CuentaHttpRepositorio {
    // Sin `window`, `baseUrl()` toma la rama absoluta, que es lo que el `fetch` de Node exige bajo
    // jsdom. Mismo motivo que en `sesion-http.repositorio.spec.ts`, que lo explica.
    vi.stubGlobal('window', undefined);
    vi.stubGlobal('fetch', vi.fn(async () => respuesta));
    return new CuentaHttpRepositorio();
  }

  function problema(estado: number, codigo: string): Response {
    return new Response(JSON.stringify({ codigo, status: estado }), {
      status: estado,
      headers: { 'Content-Type': 'application/problem+json' },
    });
  }

  describe('verificarCorreo', () => {
    it('el 422 sale como ErrorHttp de 4xx, que es lo que hace viva la rama del enlace vencido', async () => {
      const repositorio = conRespuesta(problema(422, 'TOKEN_VERIFICACION_CORREO_INVALIDO'));

      await expect(repositorio.verificarCorreo('vencido')).rejects.toSatisfy(
        (error: unknown) => error instanceof ErrorHttp && error.estado === 422,
      );
    });

    it('el 429 sale como DemasiadosIntentosError, no como un enlace malo', async () => {
      const repositorio = conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO'));

      await expect(repositorio.verificarCorreo('bueno')).rejects.toBeInstanceOf(
        DemasiadosIntentosError,
      );
    });

    it('el 204 resuelve', async () => {
      const repositorio = conRespuesta(new Response(null, { status: 204 }));

      await expect(repositorio.verificarCorreo('bueno')).resolves.toBeUndefined();
    });
  });

  describe('restablecerClave', () => {
    it('el 422 sale como ErrorHttp de 4xx', async () => {
      const repositorio = conRespuesta(problema(422, 'TOKEN_RECUPERACION_CLAVE_INVALIDO'));

      await expect(repositorio.restablecerClave('vencido', 'nueva')).rejects.toSatisfy(
        (error: unknown) => error instanceof ErrorHttp && error.estado === 422,
      );
    });

    it('el 429 sale como DemasiadosIntentosError', async () => {
      const repositorio = conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO'));

      await expect(repositorio.restablecerClave('bueno', 'nueva')).rejects.toBeInstanceOf(
        DemasiadosIntentosError,
      );
    });

    it('el 500 tambien sale como ErrorHttp, y por su estado se atribuye al servidor', async () => {
      const repositorio = conRespuesta(new Response(null, { status: 500 }));

      await expect(repositorio.restablecerClave('bueno', 'nueva')).rejects.toSatisfy(
        (error: unknown) => error instanceof ErrorHttp && error.estado === 500,
      );
    });
  });

  describe('registrar', () => {
    it('el 409 sigue saliendo con su tipo propio', async () => {
      const repositorio = conRespuesta(problema(409, 'CORREO_YA_REGISTRADO'));

      await expect(repositorio.registrar('a@b.co', 'clave', true)).rejects.toBeInstanceOf(
        CorreoYaRegistradoError,
      );
    });

    it('el 429 sale como DemasiadosIntentosError', async () => {
      const repositorio = conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO'));

      await expect(repositorio.registrar('a@b.co', 'clave', true)).rejects.toBeInstanceOf(
        DemasiadosIntentosError,
      );
    });
  });

  describe('reenviarVerificacion', () => {
    it('el 429 sale como DemasiadosIntentosError', async () => {
      const repositorio = conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO'));

      await expect(repositorio.reenviarVerificacion('a@b.co')).rejects.toBeInstanceOf(
        DemasiadosIntentosError,
      );
    });
  });
});
