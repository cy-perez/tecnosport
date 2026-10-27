import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { SesionStore } from '../../core/autenticacion/sesion.store';

/**
 * Espera `SesionStore.listo` antes de decidir — sin eso, en el primer
 * pintado del navegador `sesion()` todavía es `null` mientras el refresco
 * silencioso está en vuelo, y esto redirigiría al login a alguien que sí
 * tiene una sesión válida por cookie.
 *
 * **En el servidor no decide nada: deja pasar.** `SesionStore` resuelve
 * siempre "sin sesión" en SSR, a propósito y documentado — no reenvía la
 * cookie `HttpOnly` de refresco. Con eso, este guardia redirigía *siempre*
 * al login durante el SSR, el navegador seguía ese 302, e hidrataba ya en
 * la pantalla de login: recargar cualquier página de `/admin` sacaba al
 * administrador aunque su sesión fuera válida. El refresco silencioso
 * terminaba bien un instante después —el encabezado mostraba "Cerrar
 * sesión"— pero la navegación ya se había perdido.
 *
 * Abstenerse es lo correcto: el servidor no tiene la información para
 * decidir, y el cliente vuelve a evaluar este guardia al hidratar, ya con
 * el refresco resuelto. Lo que se sirve mientras tanto es el armazón de la
 * pantalla sin datos — cada consulta de `/admin` va autenticada y el
 * backend exige el rol en cada endpoint (regla dura #7), así que la
 * protección real nunca estuvo aquí. `/admin` tampoco necesita SEO.
 */
export const adminGuard: CanActivateFn = async (route, estado) => {
  const sesionStore = inject(SesionStore);
  const router = inject(Router);

  if (!isPlatformBrowser(inject(PLATFORM_ID))) {
    return true;
  }

  await sesionStore.listo;

  if (!sesionStore.esAdmin()) {
    return alInicioDeSesion(router, route, estado);
  }
  // Sesión restringida (ADR-0096): el servidor exige el segundo factor y esta cuenta no lo tiene.
  // Todo `/admin/**` le responde 403, así que dejarla pasar es pintar un panel que falla entero.
  // Se la lleva a activarlo, que es lo único que su token permite.
  if (sesionStore.sesion()?.segundoFactorPorActivar) {
    return router.parseUrl(`/${idiomaDe(route)}/admin/activar-segundo-factor`);
  }
  return true;
};

/**
 * La pantalla de activación del segundo factor: cualquier `ADMIN`, con la sesión restringida o
 * completa —la segunda es quien lo activa por su cuenta sin que el servidor lo exija—. Sin sesión
 * de administrador, al inicio de sesión como el resto del panel. Mismas razones que
 * {@link adminGuard} para no decidir en el servidor.
 */
export const activacionSegundoFactorGuard: CanActivateFn = async (route, estado) => {
  const sesionStore = inject(SesionStore);
  const router = inject(Router);

  if (!isPlatformBrowser(inject(PLATFORM_ID))) {
    return true;
  }

  await sesionStore.listo;

  return sesionStore.esAdmin() ? true : alInicioDeSesion(router, route, estado);
};

function idiomaDe(route: ActivatedRouteSnapshot): string {
  return route.paramMap.get('lang') ?? route.parent?.paramMap.get('lang') ?? 'es';
}

function alInicioDeSesion(
  router: Router,
  route: ActivatedRouteSnapshot,
  estado: RouterStateSnapshot | undefined,
) {
  const destino = router.parseUrl(`/${idiomaDe(route)}/admin/iniciar-sesion`);
  // A dónde iba, para volver ahí después de iniciar sesión. Sin esto, abrir un
  // enlace directo a una pantalla de /admin sin sesión terminaba en el panel y
  // había que volver a buscar el enlace — encontrado probando desde un teléfono,
  // que es justo el caso donde uno llega por enlace.
  if (estado?.url) {
    destino.queryParams = { destino: estado.url };
  }
  return destino;
}
