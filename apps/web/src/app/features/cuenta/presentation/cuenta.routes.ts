import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_CUENTA } from '../domain/repositorio-cuenta.puerto';
import { CuentaHttpRepositorio } from '../infrastructure/cuenta-http.repositorio';

// El binding puerto -> implementación vive aquí, no en presentation/: "el
// proveedor de la ruta decide la implementación" (apps/web/CLAUDE.md), mismo
// criterio que checkout.routes.ts.
export const cuentaRoutes: Routes = [
  {
    path: '',
    providers: [{ provide: REPOSITORIO_CUENTA, useClass: CuentaHttpRepositorio }, provideTranslocoScope('cuenta')],
    children: [
      {
        path: 'iniciar-sesion',
        loadComponent: () =>
          import('./iniciar-sesion/iniciar-sesion-cliente.page').then((m) => m.IniciarSesionClientePage),
      },
      {
        path: 'registro',
        loadComponent: () => import('./registro/registro-cliente.page').then((m) => m.RegistroClientePage),
      },
      {
        path: 'verificar-correo',
        loadComponent: () =>
          import('./verificar-correo/verificar-correo.page').then((m) => m.VerificarCorreoPage),
      },
      {
        path: 'recuperar-clave',
        loadComponent: () =>
          import('./recuperar-clave/recuperar-clave.page').then((m) => m.RecuperarClavePage),
      },
      {
        path: 'restablecer-clave',
        loadComponent: () =>
          import('./restablecer-clave/restablecer-clave.page').then((m) => m.RestablecerClavePage),
      },
    ],
  },
];
