import type { Request, RequestHandler, Response } from 'express';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

/**
 * Reenvía `/api/**` al backend desde el propio servidor SSR.
 *
 * Existe porque en el ambiente desplegado la web y la API son dos servicios con dominios
 * distintos, y **el sitio no funciona así**: `baseUrl()` es relativa en el navegador a propósito
 * (`core/http/base-url.ts`) y la cookie de refresco es `HttpOnly`, `SameSite=Lax` y acotada a
 * `/api/v1/auth`. Con la API en otro dominio, el navegador no manda esa cookie y la sesión no
 * sobrevive a un F5; arreglarlo por el otro lado costaría CORS, `SameSite=None` y tocar código
 * del cliente para nada. En producción esto lo hará el balanceador; en dev, donde un balanceador
 * cuesta más que todo lo demás junto, lo hace este proxy.
 *
 * Es además el proxy que le faltó a la primera medición de Lighthouse, que salió inválida porque
 * el build servido no enruta `/api` y la ficha se quedaba sin producto.
 *
 * Se monta solo si `API_URL_PUBLICA` está definida. En local no lo está: ahí el enrutamiento lo
 * hace `proxy.conf.json` de `ng serve`.
 */

type Buscar = typeof fetch;

const SIN_CUERPO = new Set(['GET', 'HEAD']);

/**
 * Cabeceras de salto: describen la conexión con *este* servidor, no la petición del visitante.
 * Reenviarlas corrompe la conexión de más allá.
 *
 * `x-forwarded-for` **no** está en la lista, y es deliberado: el backend lo lee para limitar
 * intentos por IP y para dejar constancia de la IP en `autorizacion_datos` (`ADR-0019`). Si el
 * proxy lo borrara, todas las peticiones del sitio le llegarían al backend con la misma IP —la de
 * este servidor— y el limitador pasaría a contar a todos los visitantes como uno solo.
 */
const CABECERAS_QUE_NO_VIAJAN = new Set([
  'host',
  'connection',
  'keep-alive',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
]);

/**
 * `content-encoding` y `content-length` describen el cuerpo **comprimido** que `fetch` ya
 * descomprimió al leerlo: copiarlos tal cual deja al navegador esperando bytes que no van a
 * llegar. `set-cookie` se copia aparte porque puede venir repetida y `Headers` la colapsa en una
 * sola cadena separada por comas — que es exactamente como se pierde la cookie de refresco.
 */
const CABECERAS_QUE_NO_VUELVEN = new Set([
  'connection',
  'content-encoding',
  'content-length',
  'set-cookie',
  'transfer-encoding',
]);

export function cabecerasParaElBackend(cabeceras: Request['headers']): Headers {
  const salida = new Headers();
  for (const [nombre, valor] of Object.entries(cabeceras)) {
    if (valor === undefined || CABECERAS_QUE_NO_VIAJAN.has(nombre.toLowerCase())) continue;
    for (const uno of Array.isArray(valor) ? valor : [valor]) salida.append(nombre, uno);
  }
  return salida;
}

export function copiarCabecerasAlNavegador(respuesta: Response, cabeceras: Headers): void {
  cabeceras.forEach((valor, nombre) => {
    if (!CABECERAS_QUE_NO_VUELVEN.has(nombre.toLowerCase())) respuesta.setHeader(nombre, valor);
  });
  const cookies = cabeceras.getSetCookie();
  if (cookies.length > 0) respuesta.setHeader('set-cookie', cookies);
}

export function crearProxyApi(destino: string, buscar: Buscar = fetch): RequestHandler {
  // `API_URL_PUBLICA` incluye la ruta base (`.../api/v1`), igual que la lee `baseUrl()`. Aquí solo
  // interesa el origen: la ruta la trae la petición del visitante.
  const origen = new URL(destino).origin;

  return (req, res) => {
    reenviar(origen, buscar, req, res).catch((error: unknown) => {
      // A la salida estándar de errores: en Cloud Run eso es un registro, y sin él un 502 no dice
      // nada sobre qué se cayó.
      console.error('proxy /api:', error);
      // Un backend caído no es un error del servidor de la web: es una dependencia que no
      // responde, y decirlo con 502 le permite al cliente distinguirlo de un 500 propio.
      if (res.headersSent) {
        res.end();
        return;
      }
      res.status(502).json({ codigo: 'API_NO_DISPONIBLE' });
    });
  };
}

async function reenviar(
  origen: string,
  buscar: Buscar,
  req: Request,
  res: Response,
): Promise<void> {
  const respuesta = await buscar(origen + req.originalUrl, {
    method: req.method,
    headers: cabecerasParaElBackend(req.headers),
    // El cuerpo se reenvía en flujo, sin juntarlo en memoria. `duplex: 'half'` es obligatorio al
    // mandar un flujo y todavía no está en los tipos de TypeScript, de ahí el molde.
    body: SIN_CUERPO.has(req.method) ? undefined : (Readable.toWeb(req) as unknown as BodyInit),
    duplex: 'half',
    // Una redirección la decide el cliente, no el proxy.
    redirect: 'manual',
  } as RequestInit);

  res.status(respuesta.status);
  copiarCabecerasAlNavegador(res, respuesta.headers);

  if (!respuesta.body) {
    res.end();
    return;
  }
  await pipeline(Readable.fromWeb(respuesta.body as never), res);
}
