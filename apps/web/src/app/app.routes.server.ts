import { RenderMode, ServerRoute } from '@angular/ssr';

// El catálogo es contenido vivo (precio, existencia, productos nuevos):
// SSR por solicitud, no prerender en build. Prerenderizar exigiría además
// enumerar los valores de :lang con getPrerenderParams, que no aplica aquí.
export const serverRoutes: ServerRoute[] = [
  // `/admin` se renderiza solo en el cliente. `adminGuard` se abstiene en el
  // servidor —y con razón: el SSR no reenvía la cookie HttpOnly de refresco, así
  // que no tiene con qué decidir—, de modo que el servidor pintaba la pantalla
  // de administración y un instante después el cliente redirigía al login. Quien
  // abre un enlace directo sin sesión ve la pantalla protegida parpadear:
  // encontrado probando el asistente de captura desde un teléfono.
  //
  // No renderizarla en el servidor lo cierra de raíz, y no cuesta nada: `/admin`
  // no necesita SEO ni primer pintado rápido, y sus datos ya venían del cliente
  // (cada consulta va autenticada). La protección real siempre estuvo en el
  // backend, que exige el rol en cada endpoint (regla dura #7); esto es sobre lo
  // que se alcanza a ver, no sobre a qué se alcanza a acceder.
  {
    path: ':lang/admin/**',
    renderMode: RenderMode.Client,
  },
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
