import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { cn } from '../cn';
import type { IconoMarca } from './marcas.generado';

/**
 * Dibuja el logo de una marca (Facebook, Instagram, WhatsApp) como SVG en línea.
 *
 * **Es otro componente y no un modo de `ts-icono` a propósito.** `ts-icono` dibuja iconos de
 * trazo: `fill="none"`, `stroke="currentColor"`, grosor 1,5 — las reglas de iconografía de
 * `docs/04-ui-marca.md`. Un logo de marca se distribuye como un **path relleno**, y pasarlo por
 * ese `<svg>` no lo pintaría distinto: lo dejaría invisible (relleno ninguno) o deformado (un
 * contorno de 1,5 sobre una silueta pensada para rellenarse). Meter un `[relleno]` en `ts-icono`
 * habría convertido un componente con una regla en uno con una excepción.
 *
 * Lo que sí comparte, porque no es decoración: caja de 24, `currentColor` para heredar el color
 * del texto en los dos temas, tamaño desde la escala de espacio, y `aria-hidden` con
 * `focusable="false"` — **el logo nunca es el nombre accesible**. Eso le toca al enlace que lo
 * contiene, con su texto traducido: un enlace cuyo nombre accesible fuera el logo diría
 * "Facebook" sin decir que lleva al perfil de la tienda.
 *
 * Los datos salen de `marcas.generado.ts`, que produce `npm run iconos-marca` desde
 * `simple-icons`. Ver el encabezado de `tools/generar-iconos-marca.mjs` para por qué la librería
 * no entra en producción.
 */
@Component({
  selector: 'ts-icono-marca',
  template: `
    <svg
      [class]="clases()"
      viewBox="0 0 24 24"
      fill="currentColor"
      stroke="none"
      aria-hidden="true"
      focusable="false"
    >
      <svg:path [attr.d]="marca().trazo" />
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsIconoMarca {
  /** El logo, importado de `marcas.generado.ts`. */
  readonly marca = input.required<IconoMarca>();

  /** Para cambiar el tamaño: `size-16`, `size-32`. Nunca un píxel suelto. */
  readonly clase = input('');

  protected readonly clases = computed(() => cn('inline-block shrink-0 size-24', this.clase()));
}
