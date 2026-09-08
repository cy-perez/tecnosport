import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { cn } from '../cn';
import { iconoVisto } from '../icono/iconos';
import { TsIcono } from '../icono/ts-icono';

/**
 * Casilla de verificación.
 *
 * Se construyó cuando apareció su consumidor, que es la regla del proyecto, y
 * el consumidor apareció midiendo: la casilla de "reducir movimiento" del pie
 * era un `<input type="checkbox">` nativo sin estilar de **13 × 13 px**, y su
 * `<label>` solo llegaba a 19 px de alto. `docs/04-ui-marca.md` pide 44 px y
 * WCAG 2.2 AA (2.5.8) exige 24 × 24 como mínimo absoluto: el control de
 * accesibilidad del sitio era el que incumplía accesibilidad. No lo veía
 * ninguna prueba —jsdom no hace layout— ni el escritorio, donde el ratón
 * perdona un objetivo pequeño.
 *
 * **El objetivo táctil es la etiqueta entera, no la caja.** `min-h-tactil` va
 * en el `<label>`, que es lo que de verdad se pulsa; la caja mide 24 px porque
 * agrandarla más la volvería un cuadrado desproporcionado junto a un texto de
 * `text-sm`. Así el área efectiva supera los 44 px sin deformar el dibujo.
 *
 * **`sobreMarca` no es un capricho de tema.** El pie es una franja de
 * `--color-marca`, y ahí un anillo de `--color-foco` es invisible en tema
 * claro (los dos colores son `#1B1F26`) — el mismo problema que ya obligó a
 * crear `anillo-foco-sobre-marca`. Con la bandera, la casilla usa el par
 * `sobre-marca` / `marca`, que `npm run contrastes` valida en 14,64:1.
 *
 * Tonto de verdad: la etiqueta llega **ya traducida** y el estado lo manda
 * quien lo usa. No sabe qué significa estar marcada.
 *
 * No implementa `ControlValueAccessor`, a diferencia de `ts-campo` y
 * `ts-select`. No es un olvido: su único consumidor no usa Angular Forms, y
 * cablear un CVA que nadie registra es exactamente el código especulativo que
 * este componente estuvo esperando no ser. Se añade el día que una casilla
 * entre en un formulario.
 */
const CAJA = 'col-start-1 row-start-1 size-24 appearance-none border';

/** Sobre una superficie normal: borde de control, relleno primario al marcar. */
const CAJA_NORMAL =
  'anillo-foco border-ts-borde-control bg-ts-superficie checked:border-ts-primario checked:bg-ts-primario';

/** Sobre la franja de marca: el par `sobre-marca` / `marca`, ya validado. */
const CAJA_SOBRE_MARCA =
  'anillo-foco-sobre-marca border-ts-sobre-marca bg-transparent checked:bg-ts-sobre-marca';

@Component({
  selector: 'ts-checkbox',
  imports: [TsIcono],
  templateUrl: './ts-checkbox.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsCheckbox {
  /**
   * No se llama `id` por el mismo motivo que en `ts-campo`: Angular no
   * renombra un `input()` en el DOM, así que el host acabaría con el mismo
   * `id` que el control real y cualquier `<label for>` apuntaría al host.
   */
  readonly idCasilla = input.required<string>();
  /** Texto visible, **ya traducido** por quien llama. */
  readonly etiqueta = input.required<string>();
  readonly marcado = input.required<boolean>();
  readonly deshabilitado = input(false);
  /** Para las franjas de `--color-marca`, donde el anillo normal no se ve. */
  readonly sobreMarca = input(false);

  readonly marcadoCambio = output<boolean>();

  protected readonly iconoVisto = iconoVisto;

  protected readonly clasesEtiqueta = computed(() =>
    cn(
      'inline-flex min-h-tactil items-center gap-8 text-sm',
      this.deshabilitado() ? 'cursor-not-allowed text-ts-deshabilitado' : 'cursor-pointer',
    ),
  );

  protected readonly clasesCaja = computed(() =>
    cn(
      CAJA,
      this.sobreMarca() ? CAJA_SOBRE_MARCA : CAJA_NORMAL,
      this.deshabilitado()
        ? 'cursor-not-allowed border-ts-deshabilitado checked:bg-ts-deshabilitado'
        : 'cursor-pointer',
    ),
  );

  /** El color del visto tiene que contrastar con el relleno de la caja. */
  protected readonly claseVisto = computed(() =>
    cn('size-16', this.sobreMarca() ? 'text-ts-marca' : 'text-ts-sobre-primario'),
  );
}
