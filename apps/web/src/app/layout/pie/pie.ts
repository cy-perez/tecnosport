import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

const CLAVE_ALMACEN = 'ts-movimiento-reducido';

/**
 * Franja grande de marca (`docs/04-ui-marca.md`): fondo `--color-marca` en
 * los dos temas, nunca ámbar — por eso solo lleva el logo negativo, sin el
 * intercambio positivo/negativo que sí usa `Encabezado`.
 *
 * Datos legales de `docs/00-producto.md` (persona natural, sin sigla
 * societaria — `docs/08-seguridad-legal.md`).
 *
 * De los tres controles de accesibilidad que pide `docs/04-ui-marca.md`,
 * solo "reducir movimiento" se construyó aquí. Los otros dos siguen
 * pendientes, cada uno por una razón real, no por descuido:
 * - **Alto contraste** necesita una paleta que `tokens.json` no define —
 *   no es una decisión de quien programa.
 * - **Tamaño de texto**: los tokens `--texto-*` de `tokens.css` están en
 *   `px`, no en `rem` (verificado, no de memoria) — escalar la fuente raíz
 *   no los mueve. La única vía sin tocar `tokens.css` a mano (regla dura
 *   #3) sería `zoom`/`transform`, que rompe el layout y no es soporte real
 *   de accesibilidad. El zoom nativo del navegador ya cumple el mínimo de
 *   `docs/04-ui-marca.md` ("el texto aguanta 200% de zoom"); un control
 *   propio de verdad exige rehacer esos tokens a `rem` en el kit — fuera
 *   del alcance de una sesión de frontend.
 *
 * "Reducir movimiento" no tiene ninguno de esos dos problemas: la regla ya
 * existe en `tokens.css` bajo `prefers-reduced-motion`, y `styles.scss`
 * (nunca tokens.css) la repite bajo `[data-movimiento="reducido"]` para que
 * quien no cambió la preferencia de su sistema operativo pueda igual
 * pedirla en este sitio.
 */
@Component({
  selector: 'app-pie',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './pie.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pie {
  protected readonly idioma = inject(TranslocoService).activeLang;
  protected readonly anioActual = new Date().getFullYear();
  protected readonly movimientoReducido = signal(false);

  constructor() {
    afterNextRender(() => {
      const guardado = window.localStorage.getItem(CLAVE_ALMACEN) === 'true';
      if (guardado) {
        this.movimientoReducido.set(true);
        document.documentElement.setAttribute('data-movimiento', 'reducido');
      }
    });
  }

  protected alternarMovimientoReducido(): void {
    const nuevo = !this.movimientoReducido();
    this.movimientoReducido.set(nuevo);
    window.localStorage.setItem(CLAVE_ALMACEN, String(nuevo));
    document.documentElement.setAttribute('data-movimiento', nuevo ? 'reducido' : 'normal');
  }
}
