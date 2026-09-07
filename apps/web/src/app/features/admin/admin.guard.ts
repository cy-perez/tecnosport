import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
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

  if (sesionStore.esAdmin()) {
    return true;
  }

  const idioma = route.paramMap.get('lang') ?? route.parent?.paramMap.get('lang') ?? 'es';
  const destino = router.parseUrl(`/${idioma}/admin/iniciar-sesion`);
  // A dónde iba, para volver ahí después de iniciar sesión. Sin esto, abrir un
  // enlace directo a una pantalla de /admin sin sesión terminaba en el panel y
  // había que volver a buscar el enlace — encontrado probando el asistente de
  // captura desde un teléfono, que es justo el caso donde uno llega por enlace.
  if (estado?.url) {
    destino.queryParams = { destino: estado.url };
  }
  return destino;
};
