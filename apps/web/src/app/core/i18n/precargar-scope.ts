import { inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

/**
 * Precarga el JSON de un scope perezoso de Transloco antes de que la ruta cree
 * su componente. Mismo criterio que ADR-0011 con las consultas de TanStack
 * Query: lo que la primera pantalla necesita se pide en el `resolve`, no
 * dentro del componente.
 *
 * Sin esto, un scope declarado con `provideTranslocoScope` llega por HTTP
 * *después* del primer render. El `TranslocoPipe` lo tolera —se resuscribe y
 * repinta—, pero cualquier lectura imperativa de una traducción durante ese
 * primer render se queda con lo que haya: la clave cruda si se usó
 * `translate()`, o vacío si se usó `translateObjectSignal`.
 *
 * Traga el error a propósito, igual que `prefetchQuery`: un JSON de idioma que
 * no responde no puede tumbar la navegación. El componente igual repinta
 * cuando llegue.
 */
export function precargarScopeI18n(scope: string): Promise<unknown> {
  const transloco = inject(TranslocoService);
  return firstValueFrom(transloco.load(`${scope}/${transloco.getActiveLang()}`)).catch(() => undefined);
}
