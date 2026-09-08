import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarScopeI18n } from '../../../core/i18n/precargar-scope';

/**
 * Las tres páginas legales comparten componente y se distinguen por `data.documento`. No es
 * ahorro de archivos: los tres documentos son estructuralmente idénticos —título, entradilla y
 * una lista de secciones numeradas— y darles tres componentes iguales significaría que el día que
 * uno cambie de maquetación los otros dos se queden atrás sin que nadie lo note.
 *
 * Se precarga el scope en el `resolve` (ADR-0011): sin eso el primer render llega con las
 * secciones vacías, porque se leen con `translateObjectSignal` y no con el pipe.
 */
export const legalesRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('legales')],
    resolve: { i18n: () => precargarScopeI18n('legales') },
    children: [
      {
        path: 'privacidad',
        data: {
          documento: 'privacidad',
          seo: { clave: 'legales.seo.privacidad', indexable: true },
        },
        loadComponent: () => import('./documento/documento-legal.page').then((m) => m.DocumentoLegalPage),
      },
      {
        path: 'terminos',
        data: {
          documento: 'terminos',
          seo: { clave: 'legales.seo.terminos', indexable: true },
        },
        loadComponent: () => import('./documento/documento-legal.page').then((m) => m.DocumentoLegalPage),
      },
      {
        path: 'cookies',
        data: {
          documento: 'cookies',
          seo: { clave: 'legales.seo.cookies', indexable: true },
        },
        loadComponent: () => import('./documento/documento-legal.page').then((m) => m.DocumentoLegalPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'terminos' },
    ],
  },
];
