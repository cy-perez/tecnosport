import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarScopeI18n } from '../../../core/i18n/precargar-scope';

/**
 * Las dos páginas de ayuda de la vitrina: preguntas frecuentes y contacto.
 *
 * <p><b>No existían.</b> El pie enlazaba —y la gente busca— tres cosas que el sitio no tenía: las
 * preguntas frecuentes, el estado del pedido y una forma de contactar. La tercera vivía repartida
 * entre el bloque de contacto del pie y el numeral 12 de los términos, que es donde nadie la busca.
 *
 * <p>Funcionalidad propia y no una carpeta de `legales`, aunque casi todo lo que dicen salga de los
 * documentos legales: estos textos <b>resumen para que se lean</b>, y los legales <b>obligan</b>.
 * Mezclarlos sería invitar a editar un documento que tiene versión y fecha como si fuera una
 * página de ayuda. La página lo dice en voz alta, con enlace al documento.
 *
 * <p>Sin proveedores de puerto porque no hay ninguno: las dos páginas son texto y enlaces. Los
 * datos del negocio —correo, WhatsApp, teléfono, NIT, dirección, horario— no se copian aquí: se
 * leen de las claves `pie.*` del paquete raíz de i18n, que son las que la Ley 1480 obliga a
 * publicar y las únicas que `npm run datos-negocio` vigila. Una segunda copia sería exactamente la
 * divergencia que esa herramienta existe para evitar.
 *
 * <p>Se precarga el scope en el `resolve` (ADR-0011): las dos pantallas leen su contenido con
 * `translateObjectSignal`, no con el pipe, así que sin la precarga el primer render sale vacío.
 */
export const ayudaRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('ayuda')],
    resolve: { _i18n: () => precargarScopeI18n('ayuda') },
    children: [
      {
        path: 'preguntas-frecuentes',
        data: { seo: { clave: 'ayuda.seo.preguntas', indexable: true } },
        loadComponent: () =>
          import('./preguntas-frecuentes/preguntas-frecuentes.page').then(
            (m) => m.PreguntasFrecuentesPage,
          ),
      },
      {
        path: 'contacto',
        data: { seo: { clave: 'ayuda.seo.contacto', indexable: true } },
        loadComponent: () => import('./contacto/contacto.page').then((m) => m.ContactoPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'preguntas-frecuentes' },
    ],
  },
];
