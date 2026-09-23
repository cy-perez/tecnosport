import { ErrorHttp } from '../http/respuesta-http';
import {
  ClaveActualIncorrectaError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from './sesion.errores';
import { SesionHttpRepositorio } from './sesion-http.repositorio';

/**
 * El refresco silencioso del arranque distingue tres respuestas y las tres importan: `204` es
 * "nunca hubo sesión" (visita anónima, el caso normal), `401` es "la cookie que llegó no sirve",
 * y cualquier otro fallo sí es un error que no se puede tragar. Las dos primeras se resuelven
 * igual —sin sesión— pero se prueban aparte porque el servidor las emite por motivos distintos
 * y el día que alguien "simplifique" el 204 vuelve el error de consola en cada visita.
 */
describe('SesionHttpRepositorio.refrescar', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response): SesionHttpRepositorio {
    // Sin `window`, `baseUrl()` toma la rama absoluta (`core/http/base-url.ts`). Hace falta: en
    // el navegador la base es relativa a propósito, pero el `fetch` de Node que corre bajo jsdom
    // exige un origen y revienta al construir la petición, antes de llegar al doble. La base
    // absoluta es lo que aporta el navegador de verdad; aquí se aporta a mano.
    vi.stubGlobal('window', undefined);
    vi.stubGlobal('fetch', vi.fn(async () => respuesta));
    return new SesionHttpRepositorio();
  }

  it('devuelve la sesión cuando el servidor responde 200', async () => {
    const repositorio = conRespuesta(
      new Response(JSON.stringify({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(repositorio.refrescar()).resolves.toEqual({
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt',
    });
  });

  it('devuelve null sin sesión que refrescar (204), no un error', async () => {
    const repositorio = conRespuesta(new Response(null, { status: 204 }));

    await expect(repositorio.refrescar()).resolves.toBeNull();
  });

  it('devuelve null cuando la cookie que llegó no sirve (401)', async () => {
    const repositorio = conRespuesta(
      new Response(JSON.stringify({ codigo: 'SESION_DE_REFRESCO_INVALIDA' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(repositorio.refrescar()).resolves.toBeNull();
  });

  it('lanza ErrorHttp con el código cuando el servidor falla de verdad', async () => {
    const repositorio = conRespuesta(new Response(null, { status: 500 }));

    // Se afirma el tipo y el código, no el texto: el mensaje es un diagnóstico para quien
    // programa y la pantalla traduce su propia clave (`core/http/respuesta-http.ts`).
    const error = await repositorio.refrescar().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorHttp);
    expect((error as ErrorHttp).estado).toBe(500);
  });
});

/**
 * `POST /auth/clave` responde **401 por dos motivos distintos**: la clave actual equivocada y el
 * token de acceso vencido. Los separa el `codigo` del cuerpo, no el estado — si se confundieran,
 * a quien escribió bien su clave le diriamos que está mal y se pondría a buscar un problema que
 * no existe.
 */
describe('SesionHttpRepositorio.iniciarSesion', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response): SesionHttpRepositorio {
    vi.stubGlobal('window', undefined);
    vi.stubGlobal('fetch', vi.fn(async () => respuesta));
    return new SesionHttpRepositorio();
  }

  it('el 429 del limitador no se confunde con credenciales malas', async () => {
    // Antes caia en `ErrorHttp` y la pantalla lo pintaba como "correo o clave incorrectos", que
    // es lo contrario de lo que pasa: la clave esta bien y lo que hay que hacer es esperar.
    const repositorio = conRespuesta(
      new Response(JSON.stringify({ codigo: 'LIMITE_DE_INTENTOS_EXCEDIDO' }), {
        status: 429,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    );

    await expect(repositorio.iniciarSesion('admin@tecnosport.co', 'la-buena')).rejects.toBeInstanceOf(
      DemasiadosIntentosError,
    );
  });

  it('el 401 de verdad sigue siendo un ErrorHttp', async () => {
    const repositorio = conRespuesta(
      new Response(JSON.stringify({ codigo: 'CREDENCIALES_INVALIDAS' }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    );

    await expect(repositorio.iniciarSesion('admin@tecnosport.co', 'mala')).rejects.toBeInstanceOf(
      ErrorHttp,
    );
  });
});

describe('SesionHttpRepositorio.cambiarClave', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response): SesionHttpRepositorio {
    vi.stubGlobal('window', undefined);
    vi.stubGlobal('fetch', vi.fn(async () => respuesta));
    return new SesionHttpRepositorio();
  }

  function problema(estado: number, codigo: string): Response {
    return new Response(JSON.stringify({ codigo, status: estado }), {
      status: estado,
      headers: { 'Content-Type': 'application/problem+json' },
    });
  }

  it('devuelve la sesión nueva que abre el servidor', async () => {
    const repositorio = conRespuesta(
      new Response(JSON.stringify({ usuarioId: 'u1', rol: 'ADMIN', accessToken: 'jwt.nuevo' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(repositorio.cambiarClave('jwt.viejo', 'vieja', 'nueva')).resolves.toEqual({
      usuarioId: 'u1',
      rol: 'ADMIN',
      accessToken: 'jwt.nuevo',
    });
  });

  it('401 con CREDENCIALES_INVALIDAS es la clave actual equivocada', async () => {
    const repositorio = conRespuesta(problema(401, 'CREDENCIALES_INVALIDAS'));

    await expect(repositorio.cambiarClave('jwt', 'mal', 'nueva')).rejects.toBeInstanceOf(
      ClaveActualIncorrectaError,
    );
  });

  it('401 con NO_AUTENTICADO es la sesión vencida, no la clave', async () => {
    const repositorio = conRespuesta(problema(401, 'NO_AUTENTICADO'));

    await expect(repositorio.cambiarClave('jwt.vencido', 'vieja', 'nueva')).rejects.toBeInstanceOf(
      SesionExpiradaError,
    );
  });

  it('429 es el límite de intentos', async () => {
    const repositorio = conRespuesta(problema(429, 'LIMITE_DE_INTENTOS_EXCEDIDO'));

    await expect(repositorio.cambiarClave('jwt', 'vieja', 'nueva')).rejects.toBeInstanceOf(
      DemasiadosIntentosError,
    );
  });

  it('un fallo de servidor sigue siendo ErrorHttp', async () => {
    const repositorio = conRespuesta(new Response(null, { status: 500 }));

    await expect(repositorio.cambiarClave('jwt', 'vieja', 'nueva')).rejects.toBeInstanceOf(
      ErrorHttp,
    );
  });
});
