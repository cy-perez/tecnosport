import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { BrnDialogImports } from '@spartan-ng/brain/dialog';
import { TsBoton } from '../boton/ts-boton';
import { iconoCerrar } from '../icono/iconos';
import { TsIcono } from '../icono/ts-icono';

/**
 * Diálogo modal, construido sobre las primitivas headless de
 * `@spartan-ng/brain`.
 *
 * La primera versión era un `@if` en línea con `[cdkTrapFocus]`. Funcionaba,
 * pero se quedaba corta en tres cosas que Spartan sí resuelve y que un modal de
 * verdad necesita:
 *
 * - **Se pinta en un portal del CDK**, a nivel de `body`, así que no queda preso
 *   del contexto de apilamiento de su ancestro. Un modal dentro de un
 *   `position: relative` con `z-index` puede aparecer *debajo* de otra cosa.
 * - **Bloquea el desplazamiento del fondo**, que era la limitación que la
 *   versión anterior documentaba y no resolvía.
 * - **Cablea `aria-labelledby` solo**, por `brnDialogTitle`, en vez de que cada
 *   quien invente un id.
 *
 * Sigue siendo tonto: el título y la etiqueta de cerrar llegan **ya
 * traducidos**. Y el estado lo controla quien lo usa con `abierto`, no un
 * disparador interno, para que la pantalla siga siendo la dueña de cuándo se
 * abre.
 */
@Component({
  selector: 'ts-dialogo',
  imports: [BrnDialogImports, TsBoton, TsIcono],
  templateUrl: './ts-dialogo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsDialogo {
  readonly abierto = input.required<boolean>();
  readonly titulo = input.required<string>();
  /** Nombre accesible del botón de cerrar, traducido por quien llama. */
  readonly etiquetaCerrar = input.required<string>();

  readonly cerrar = output<void>();

  protected readonly iconoCerrar = iconoCerrar;

  /**
   * `brn-dialog` avisa de cada cambio de estado, incluida la apertura. Solo
   * interesa el cierre: emitirlo al abrir haría que la pantalla apagara su
   * propia señal justo después de encenderla.
   */
  protected alCambiarEstado(estado: 'open' | 'closed'): void {
    if (estado === 'closed' && this.abierto()) {
      this.cerrar.emit();
    }
  }
}
