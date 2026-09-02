import { RenderMode, ServerRoute } from '@angular/ssr';

// El catálogo es contenido vivo (precio, existencia, productos nuevos):
// SSR por solicitud, no prerender en build. Prerenderizar exigiría además
// enumerar los valores de :lang con getPrerenderParams, que no aplica aquí.
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
