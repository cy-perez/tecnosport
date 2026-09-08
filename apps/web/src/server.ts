import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from './app/core/http/base-url';
import { origenPublico } from './app/core/seo/origen-publico';
import { construirRobots, construirSitemap, PaginaDelSitio } from './sitemap/constructor';
import legalesEs from './assets/i18n/scopes/legales/es.json';
import { conTemaAplicado, leerTema } from './tema-ssr';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

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
