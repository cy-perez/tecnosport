import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

const IDIOMAS_VALIDOS = ['es', 'en'] as const;
type IdiomaValido = (typeof IDIOMAS_VALIDOS)[number];

function esIdiomaValido(valor: string | null): valor is IdiomaValido {
  return valor !== null && (IDIOMAS_VALIDOS as readonly string[]).includes(valor);
}

/**
 * Valida el prefijo :lang de la ruta, fija el idioma activo de Transloco y
 * `<html lang>` antes de activar la ruta — isomorfo, corre igual en SSR y en
 * el navegador porque solo toca el DOM (docs/05-i18n.md).
 */
export const idiomaGuard: CanActivateFn = (route) => {
  const lang = route.paramMap.get('lang');
  const router = inject(Router);

  if (!esIdiomaValido(lang)) {
    return router.parseUrl('/es');
  }

  inject(TranslocoService).setActiveLang(lang);
  inject(DOCUMENT).documentElement.lang = lang;

  return true;
};
