import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import compression from 'compression';
import express from 'express';
import { join } from 'node:path';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from './app/core/http/base-url';
import { origenPublico } from './app/core/seo/origen-publico';
import { crearProxyApi } from './proxy-api';
import { construirRobots, construirSitemap, PaginaDelSitio } from './sitemap/constructor';
import legalesEs from './assets/i18n/scopes/legales/es.json';
import { conTemaAplicado, leerTema } from './tema-ssr';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Cabeceras de seguridad, en el borde y para todo lo que sale de este servidor.
 *
 * No estaban: la respuesta del ambiente desplegado traía `content-type`, `date`, `server` y un
 * `x-powered-by` que solo sirve para anunciar con qué se construyó esto. Ninguna de las cuatro
 * cuesta nada y las cuatro cierran un ataque concreto — degradar a HTTP, adivinar el tipo de un
 * archivo servido, enmarcar el sitio para robar un clic, y filtrar la ruta completa al enlazar
 * hacia afuera.
 *
 * `X-Frame-Options: DENY` es sobre quién puede enmarcarnos, no sobre a quién enmarcamos nosotros:
 * el checkout de Wompi lo abre el navegador con la llave pública y no depende de esto.
 *
 * Sin CSP todavía, y a propósito: una política que sirva para Angular necesita nonces por
 * respuesta, y media a medias es peor que ninguna porque invita a confiar en ella.
 */
app.disable('x-powered-by');
app.use((_req, res, next) => {
  res.setHeader('strict-transport-security', 'max-age=31536000; includeSubDomains');
  res.setHeader('x-content-type-options', 'nosniff');
  res.setHeader('x-frame-options', 'DENY');
  res.setHeader('referrer-policy', 'strict-origin-when-cross-origin');
  next();
});

/**
 * Compresión, y **antes de todo lo que escriba un cuerpo**. Ni `express.static` ni el manejador
 * de Angular comprimen por su cuenta, y Cloud Run tampoco lo hace por nosotros: pedido el
 * ambiente desplegado con `Accept-Encoding: gzip, br`, ninguna respuesta traía
 * `content-encoding`. La carga inicial de la portada eran 664 KB de HTML, JS y CSS; comprimida
 * son 213 KB.
 *
 * Negocia **brotli** cuando el cliente lo acepta, que es lo que hace cualquier navegador, y ahí
 * salen esos 213 KB. Con gzip son 211 KB — brotli queda dos kilobytes *peor*, porque el paquete
 * usa una calidad de brotli baja a propósito para no gastar en CPU lo que ahorra en red. La
 * diferencia entre las dos es ruido; la que importa es contra los 664 KB de no comprimir nada.
 *
 * Va **delante del proxy de `/api`** y no solo de los estáticos, y ese orden es la parte que no
 * se ve: `fetch` descomprime la respuesta del backend al leerla, así que `proxy-api.ts` borra
 * `content-encoding` a propósito —copiarlo dejaría al navegador esperando bytes que no
 * llegan— y el JSON del catálogo terminaba viajando en claro. Doble codificación no puede
 * haber justamente porque el proxy nunca declara ninguna.
 */
app.use(compression());

/**
 * Antes que los estáticos: en el ambiente desplegado la web y la API son dos
 * servicios distintos, y el sitio necesita verlas en el mismo origen para que la cookie de sesión
 * viaje. Ver `proxy-api.ts`. En local no se monta — ahí lo hace `proxy.conf.json`.
 */
const apiPublica = process.env['API_URL_PUBLICA'];
if (apiPublica) {
  app.use('/api', crearProxyApi(apiPublica));
}

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Las páginas fijas del sitio, sin prefijo de idioma: el constructor cruza los dos.
 *
 * Las legales llevan `lastmod` y las demás no, y esa asimetría es deliberada. La fecha de vigencia
 * del documento legal **es** su última modificación —está en el propio texto, a la vista del
 * visitante—, así que declararla es decir la verdad. De la portada y de la rejilla no sabemos
 * cuándo cambiaron de verdad, y poner la fecha del despliegue sería enseñarle al rastreador que
 * nuestro `lastmod` no significa nada.
 *
 * El catálogo no está aquí: sus categorías son parámetros de consulta sobre `/productos`, no rutas
 * propias, y `rutaCanonica` las poda a propósito (ver `core/seo/enlaces-alternativos.ts`).
 */
const VIGENCIA_LEGAL = new Date(`${legalesEs.comun.version}T00:00:00Z`).toISOString();

const PAGINAS_FIJAS: readonly PaginaDelSitio[] = [
  { ruta: '' },
  { ruta: '/productos' },
  { ruta: '/legales/terminos', lastmod: VIGENCIA_LEGAL },
  { ruta: '/legales/privacidad', lastmod: VIGENCIA_LEGAL },
  { ruta: '/legales/cookies', lastmod: VIGENCIA_LEGAL },
];

/**
 * Las fichas publicadas, del backend. Devuelve lista vacía si la API no responde: un sitemap con
 * las páginas fijas sigue siendo válido y útil, mientras que un 500 deja al rastreador sin nada.
 * Mismo criterio que `precargarScopeI18n`, que también traga el error a propósito.
 */
async function fichasPublicadas(): Promise<PaginaDelSitio[]> {
  try {
    const { data } = await crearClienteContratos(baseUrl()).GET('/api/v1/mapa-del-sitio', {});
    return (data?.productos ?? []).map((producto) => ({
      ruta: `/productos/${producto.slug}`,
      lastmod: producto.actualizadoEn,
    }));
  } catch {
    return [];
  }
}

// Antes del manejador de Angular: son respuestas del sitio, no páginas del sitio, y el router de
// Angular las trataría como una ruta desconocida y las redirigiría a /es.
app.get('/sitemap.xml', (_req, res, next) => {
  fichasPublicadas()
    .then((fichas) => {
      res
        .type('application/xml')
        .set('Cache-Control', 'public, max-age=3600')
        .send(construirSitemap(origenPublico(), [...PAGINAS_FIJAS, ...fichas]));
    })
    .catch(next);
});

app.get('/robots.txt', (_req, res) => {
  res
    .type('text/plain')
    .set('Cache-Control', 'public, max-age=3600')
    .send(construirRobots(origenPublico()));
});

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then(async (response) => {
      if (!response) return next();

      const tema = leerTema(req.headers.cookie);
      if (!tema || !response.headers.get('content-type')?.includes('text/html')) {
        return writeResponseToNodeResponse(response, res);
      }

      const html = await response.text();
      const conTema = conTemaAplicado(html, tema);
      const cabeceras = new Headers(response.headers);
      cabeceras.delete('content-length');
      return writeResponseToNodeResponse(
        new Response(conTema, { status: response.status, headers: cabeceras }),
        res,
      );
    })
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
