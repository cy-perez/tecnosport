import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_PAGOS } from '../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';
import { PagoHttpRepositorio } from '../infrastructure/pago-http.repositorio';
import { PedidoHttpRepositorio } from '../infrastructure/pedido-http.repositorio';

// El binding puerto -> implementación vive aquí, no en presentation/: "el
// proveedor de la ruta decide la implementación" (apps/web/CLAUDE.md), mismo
// criterio que catalogo.routes.ts.
export const checkoutRoutes: Routes = [
  {
    path: '',
    providers: [
      { provide: REPOSITORIO_PEDIDOS, useClass: PedidoHttpRepositorio },
      { provide: REPOSITORIO_PAGOS, useClass: PagoHttpRepositorio },
      provideTranslocoScope('checkout'),
    ],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'resumen' },
      {
        path: 'resumen',
        loadComponent: () => import('./resumen/resumen.page').then((m) => m.ResumenPage),
      },
      {
        path: 'metodo-pago',
        loadComponent: () => import('./metodo-pago/metodo-pago.page').then((m) => m.MetodoPagoPage),
      },
      {
        path: 'confirmar',
        loadComponent: () => import('./confirmar/confirmar.page').then((m) => m.ConfirmarPage),
      },
      {
        path: 'retorno-wompi',
        loadComponent: () => import('./retorno-wompi/retorno-wompi.page').then((m) => m.RetornoWompiPage),
      },
      {
        path: 'estado',
        loadComponent: () => import('./estado/estado.page').then((m) => m.EstadoPage),
      },
      {
        path: 'transferencia',
        loadComponent: () => import('./transferencia/transferencia.page').then((m) => m.TransferenciaPage),
      },
    ],
  },
];
