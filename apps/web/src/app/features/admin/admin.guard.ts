import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionStore } from '../../core/autenticacion/sesion.store';

/**
 * Espera `SesionStore.listo` antes de decidir — sin eso, en el primer
 * pintado del navegador `sesion()` todavía es `null` mientras el refresco
 * silencioso está en vuelo, y esto redirigiría al login a alguien que sí
 * tiene una sesión válida por cookie.
 */
export const adminGuard: CanActivateFn = async (route) => {
  const sesionStore = inject(SesionStore);
  const router = inject(Router);

  await sesionStore.listo;

  if (sesionStore.esAdmin()) {
    return true;
  }

  const idioma = route.paramMap.get('lang') ?? route.parent?.paramMap.get('lang') ?? 'es';
  return router.parseUrl(`/${idioma}/admin/iniciar-sesion`);
};
