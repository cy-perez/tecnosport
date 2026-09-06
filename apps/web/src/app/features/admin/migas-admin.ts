import { computed, inject, Signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../core/i18n/traductor';
import { Miga } from '../../shared/ts-migas/ts-migas';

export interface SeccionAdmin {
  /** Clave de i18n de la etiqueta. Del scope `admin`, que es perezoso. */
  readonly clave: string;
  /** Segmentos bajo `/{idioma}/admin`. Sin ruta = la página actual. */
  readonly ruta?: readonly string[];
}

/**
 * Migas del panel administrativo. Todas cuelgan de "Panel", así que esa primera
 * miga se arma aquí y cada pantalla solo declara lo suyo.
 *
 * Se resuelve con `usarTraductor` y no con `transloco.translate()` directo
 * porque el scope `admin` es perezoso: dentro de un `computed`, `translate()`
 * no es reactivo y dejaría la clave cruda en pantalla (apps/web/CLAUDE.md).
 *
 * Llamar desde un inicializador de campo: usa `inject()`.
 */
export function usarMigasAdmin(secciones: readonly SeccionAdmin[]): Signal<Miga[]> {
  const transloco = inject(TranslocoService);
  const traducir = usarTraductor();

  return computed(() => {
    const traduccion = traducir();
    const idioma = transloco.activeLang();
    return [
      { etiqueta: traduccion('migas.panel'), enlace: ['/', idioma, 'admin'] },
      ...secciones.map((seccion) => ({
        etiqueta: traduccion(seccion.clave),
        ...(seccion.ruta ? { enlace: ['/', idioma, 'admin', ...seccion.ruta] } : {}),
      })),
    ];
  });
}
