import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarScopeI18n } from '../../../core/i18n/precargar-scope';

// Sin proveedores de puerto aquí, a diferencia de catalogo.routes.ts:
// `REPOSITORIO_PEDIDOS` y `REPOSITORIO_PAGOS` viven en `app.config.ts` porque
// quien los consume es `CheckoutStore`, que es `providedIn: 'root'` y por lo
// tanto no ve los proveedores de una ruta. Mismo criterio que
// `REPOSITORIO_CARRITO` y `REPOSITORIO_SESION`.
export const checkoutRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('checkout')],
    // El scope de i18n se precarga como cualquier otro dato de la primera
    // pantalla (ADR-0011): si llega después del primer render, toda etiqueta
    // que no pase por el pipe sale en blanco o con la clave cruda.
    resolve: { _i18n: () => Promise.all([precargarScopeI18n('checkout')]) },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'resumen' },
      {
        path: 'resumen',
        data: { seo: { clave: 'seo.checkout.resumen' } },
        loadComponent: () => import('./resumen/resumen.page').then((m) => m.ResumenPage),
      },
      {
        path: 'metodo-pago',
        data: { seo: { clave: 'seo.checkout.metodo_pago' } },
        loadComponent: () => import('./metodo-pago/metodo-pago.page').then((m) => m.MetodoPagoPage),
      },
      {
        path: 'confirmar',
        data: { seo: { clave: 'seo.checkout.confirmar' } },
        loadComponent: () => import('./confirmar/confirmar.page').then((m) => m.ConfirmarPage),
      },
      {
        path: 'retorno-wompi',
        data: { seo: { clave: 'seo.checkout.retorno_wompi' } },
        loadComponent: () => import('./retorno-wompi/retorno-wompi.page').then((m) => m.RetornoWompiPage),
      },
      {
        path: 'estado',
        data: { seo: { clave: 'seo.checkout.estado' } },
        loadComponent: () => import('./estado/estado.page').then((m) => m.EstadoPage),
      },
      {
        path: 'transferencia',
        data: { seo: { clave: 'seo.checkout.transferencia' } },
        loadComponent: () => import('./transferencia/transferencia.page').then((m) => m.TransferenciaPage),
      },
    ],
  },
];
