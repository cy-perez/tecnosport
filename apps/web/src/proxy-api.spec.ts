import type { Request, Response } from 'express';
import { Readable, Writable } from 'node:stream';
import { cabecerasParaElBackend, copiarCabecerasAlNavegador, crearProxyApi } from './proxy-api';

const DESTINO = 'http://api.interna:8080/api/v1';

function peticionFalsa(opciones: {
  method?: string;
  originalUrl?: string;
  headers?: Record<string, string | string[]>;
  cuerpo?: string;
}): Request {
  // Buffer y no cadena: un `req` de Express real entrega Uint8Array, y `Readable.toWeb` —lo que
  // usa el proxy para reenviar el cuerpo en flujo— rechaza cualquier otra cosa.
  const flujo = Readable.from(opciones.cuerpo ? [Buffer.from(opciones.cuerpo)] : []);
  return Object.assign(flujo, {
    method: opciones.method ?? 'GET',
    originalUrl: opciones.originalUrl ?? '/api/v1/productos',
    headers: opciones.headers ?? {},
  }) as unknown as Request;
}

/** Lo mínimo de una respuesta de Express: un flujo de escritura con estado y cabeceras. */
class RespuestaFalsa extends Writable {
  estado = 200;
  readonly cabeceras = new Map<string, string | string[]>();
  cuerpo = '';
  cuerpoJson?: unknown;

  override _write(trozo: Buffer, _codificacion: string, siguiente: () => void): void {
    this.cuerpo += trozo.toString();
    siguiente();
  }

  status(codigo: number): this {
    this.estado = codigo;
    return this;
  }

  setHeader(nombre: string, valor: string | string[]): this {
    this.cabeceras.set(nombre.toLowerCase(), valor);
    return this;
  }

  json(cuerpo: unknown): this {
    this.cuerpoJson = cuerpo;
    return this;
  }

  /** Express lo consulta antes de escribir un error; en este doble nunca se escribe a medias. */
  readonly headersSent = false;

  comoExpress(): Response {
    return this as unknown as Response;
  }
}

async function hastaQueTermine(respuesta: RespuestaFalsa): Promise<void> {
  if (respuesta.writableEnded) return;
  await new Promise((listo) => respuesta.on('finish', listo));
}

describe('cabecerasParaElBackend', () => {
  // Sin esto, el backend vería la IP de este servidor en todas las peticiones: el limitador de
  // intentos contaría a todos los visitantes como uno solo y la IP guardada en la autorización de
  // datos sería siempre la misma. Es la cabecera que NO se puede perder.
  it('conserva x-forwarded-for', () => {
    const salida = cabecerasParaElBackend({ 'x-forwarded-for': '190.0.0.1' } as Request['headers']);

    expect(salida.get('x-forwarded-for')).toBe('190.0.0.1');
  });

  it('no reenvía las cabeceras de la conexión con este servidor', () => {
    const salida = cabecerasParaElBackend({
      host: 'tecnosport.co',
      connection: 'keep-alive',
      cookie: 'refresco=abc',
    } as Request['headers']);

    expect(salida.get('host')).toBeNull();
    expect(salida.get('connection')).toBeNull();
    expect(salida.get('cookie')).toBe('refresco=abc');
  });
});

describe('copiarCabecerasAlNavegador', () => {
  // Dos Set-Cookie en una sola cabecera separada por comas es una cookie rota, y la que se rompe
  // aquí es la de refresco: la sesión no sobreviviría a un F5.
  it('no colapsa varias Set-Cookie en una sola', () => {
    const respuesta = new RespuestaFalsa();
    const cabeceras = new Headers();
    cabeceras.append('set-cookie', 'refresco=abc; Path=/api/v1/auth; HttpOnly');
    cabeceras.append('set-cookie', 'otra=1; Path=/');

    copiarCabecerasAlNavegador(respuesta.comoExpress(), cabeceras);

    expect(respuesta.cabeceras.get('set-cookie')).toEqual([
      'refresco=abc; Path=/api/v1/auth; HttpOnly',
      'otra=1; Path=/',
    ]);
  });

  // fetch descomprime el cuerpo al leerlo, así que estas dos describen algo que ya no es cierto.
  it('descarta content-encoding y content-length', () => {
    const respuesta = new RespuestaFalsa();
    const cabeceras = new Headers({
      'content-type': 'application/json',
      'content-encoding': 'gzip',
      'content-length': '99',
    });

    copiarCabecerasAlNavegador(respuesta.comoExpress(), cabeceras);

    expect(respuesta.cabeceras.get('content-type')).toBe('application/json');
    expect(respuesta.cabeceras.has('content-encoding')).toBe(false);
    expect(respuesta.cabeceras.has('content-length')).toBe(false);
  });
});

describe('crearProxyApi', () => {
  it('reenvía método, ruta con parámetros y cuerpo al origen del backend', async () => {
    let vista: { url: string; metodo?: string; cuerpo: string } | undefined;
    const buscar = vi.fn(async (url: string | URL | globalThis.Request, init?: RequestInit) => {
      const cuerpo = init?.body ? await new Response(init.body as BodyInit).text() : '';
      vista = { url: String(url), metodo: init?.method, cuerpo };
      return new Response('{"ok":true}', { status: 201 });
    });
    const respuesta = new RespuestaFalsa();

    crearProxyApi(DESTINO, buscar as unknown as typeof fetch)(
      peticionFalsa({
        method: 'POST',
        originalUrl: '/api/v1/pedidos?idioma=es',
        cuerpo: '{"lineas":[]}',
      }),
      respuesta.comoExpress(),
      () => undefined,
    );
    await hastaQueTermine(respuesta);

    // El origen sale de API_URL_PUBLICA; la ruta, de la petición del visitante.
    expect(vista?.url).toBe('http://api.interna:8080/api/v1/pedidos?idioma=es');
    expect(vista?.metodo).toBe('POST');
    expect(vista?.cuerpo).toBe('{"lineas":[]}');
    expect(respuesta.estado).toBe(201);
    expect(respuesta.cuerpo).toBe('{"ok":true}');
  });

  it('un GET no lleva cuerpo', async () => {
    let cuerpoEnviado: unknown = 'no preguntado';
    const buscar = vi.fn(async (_url: unknown, init?: RequestInit) => {
      cuerpoEnviado = init?.body;
      return new Response('[]', { status: 200 });
    });
    const respuesta = new RespuestaFalsa();

    crearProxyApi(DESTINO, buscar as unknown as typeof fetch)(
      peticionFalsa({}),
      respuesta.comoExpress(),
      () => undefined,
    );
    await hastaQueTermine(respuesta);

    expect(cuerpoEnviado).toBeUndefined();
  });

  // Con la API caída, el visitante tiene que poder distinguir "la dependencia no responde" de un
  // fallo del servidor de la web. Y las páginas fijas siguen sirviéndose: esto solo cuelga de /api.
  it('responde 502 cuando el backend no contesta', async () => {
    const buscar = vi.fn(async () => {
      throw new Error('ECONNREFUSED');
    });
    const respuesta = new RespuestaFalsa();

    crearProxyApi(DESTINO, buscar as unknown as typeof fetch)(
      peticionFalsa({}),
      respuesta.comoExpress(),
      () => undefined,
    );
    await vi.waitFor(() => expect(respuesta.cuerpoJson).toBeDefined());

    expect(respuesta.estado).toBe(502);
    expect(respuesta.cuerpoJson).toEqual({ codigo: 'API_NO_DISPONIBLE' });
  });
});
