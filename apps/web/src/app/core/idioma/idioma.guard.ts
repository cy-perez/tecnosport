import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

const IDIOMAS_VALIDOS = ['es', 'en'] as const;
type IdiomaValido = (typeof IDIOMAS_VALIDOS)[number];

function esIdiomaValido(valor: string | null): valor is IdiomaValido {
  return valor !== null && (IDIOMAS_VALIDOS as readonly string[]).includes(valor);
}

/**
 * Valida el prefijo :lang de la ruta, fija el idioma activo de Transloco y
 * `<html lang>` antes de activar la ruta — isomorfo, corre igual en SSR y en
 * el navegador porque solo toca el DOM (docs/05-i18n.md).
 *
 * **Y espera a que el JSON raíz del idioma esté cargado.** Sin esa espera, el
 * servidor serializaba la página antes de que llegara: `/es/carrito` salía sin
 * un solo texto —ni el título, ni el encabezado, ni el pie, con `wa.me/` sin
 * número—, medido en dev el 5 de octubre de 2026. Las demás rutas salían bien
 * solo de rebote, porque su `resolve` esperaba a la API y en ese tiempo la
 * traducción alcanzaba a llegar. En el navegador la espera es nula después de
 * la primera vez: Transloco guarda lo que ya cargó.
 *
 * Traga el error, como `precargarScopeI18n`: un JSON que no responde no puede
 * tumbar la navegación, y la página repinta cuando llegue.
 */
export const idiomaGuard: CanActivateFn = (route) => {
  const lang = route.paramMap.get('lang');
  const router = inject(Router);

  if (!esIdiomaValido(lang)) {
    return router.parseUrl('/es');
  }

  const transloco = inject(TranslocoService);
  transloco.setActiveLang(lang);
  inject(DOCUMENT).documentElement.lang = lang;

  return firstValueFrom(transloco.load(lang)).then(
    () => true,
    () => true,
  );
};
