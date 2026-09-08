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

  it('lanza cuando el servidor falla de verdad', async () => {
    const repositorio = conRespuesta(new Response(null, { status: 500 }));

    await expect(repositorio.refrescar()).rejects.toThrow('No se pudo refrescar la sesión.');
  });
});
