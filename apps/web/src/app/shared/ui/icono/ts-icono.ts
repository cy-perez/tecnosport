import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { IconNode } from 'lucide';
import { cn } from '../cn';

/**
 * Dibuja un icono de Lucide como SVG en línea.
 *
 * Se usa el paquete `lucide` (los datos del icono: `[etiqueta, atributos][]`)
 * y no `lucide-angular`, porque ese declara `@angular/core: 13.x - 21.x` y
 * este proyecto va en la 22 — instalarlo exigiría `--legacy-peer-deps` para
 * todo el monorepo. `lucide` no declara ningún peer.
 *
 * Los valores fijos del `<svg>` no son decoración: son las reglas de
 * iconografía de `docs/04-ui-marca.md`. `viewBox` de 24, trazo 1.5, extremos
 * redondeados, `stroke="currentColor"` para que herede el color del texto y
 * funcione en claro y en oscuro sin una regla aparte, y `aria-hidden` con
 * `focusable="false"` porque **el icono nunca es el nombre accesible**: eso le
 * toca al control que lo contiene, con su `aria-label` traducido.
 *
 * El tamaño sale de la escala de espacio (`size-24` es `var(--esp-24)`), no de
 * un píxel suelto, y se puede cambiar con `clase`.
 */
@Component({
  selector: 'ts-icono',
  templateUrl: './ts-icono.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsIcono {
  /** Los datos del icono, importados de `iconos.ts`. */
  readonly icono = input.required<IconNode>();

  /** Para cambiar el tamaño: `size-16`, `size-32`. Nunca un píxel suelto. */
  readonly clase = input('');

  protected readonly clases = computed(() => cn('inline-block shrink-0 size-24', this.clase()));
}
