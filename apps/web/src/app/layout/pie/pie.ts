import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsCheckbox } from '../../shared/ui/checkbox/ts-checkbox';
import { TsIcono } from '../../shared/ui/icono/ts-icono';
import { TsIconoMarca } from '../../shared/ui/icono/ts-icono-marca';
import {
  iconoCorreo,
  iconoHorario,
  iconoTelefono,
  iconoUbicacion,
} from '../../shared/ui/icono/iconos';
import {
  marcaFacebook,
  marcaInstagram,
  marcaWhatsapp,
} from '../../shared/ui/icono/marcas.generado';

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
  imports: [TranslocoPipe, RouterLink, TsCheckbox, TsIcono, TsIconoMarca],
  templateUrl: './pie.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pie {
  protected readonly iconoTelefono = iconoTelefono;
  protected readonly iconoCorreo = iconoCorreo;
  protected readonly iconoUbicacion = iconoUbicacion;
  protected readonly iconoHorario = iconoHorario;
  protected readonly marcaFacebook = marcaFacebook;
  protected readonly marcaInstagram = marcaInstagram;
  protected readonly marcaWhatsapp = marcaWhatsapp;

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

  /**
   * Recibe el estado en vez de alternarlo. Antes era `alternar()` y leía su
   * propia señal para decidir: con el estado viviendo también en la casilla,
   * eso son dos verdades que pueden separarse — un doble clic rápido, o un
   * futuro control que ponga el valor en vez de invertirlo. La casilla dice
   * cómo quedó; el pie lo persiste.
   */
  protected fijarMovimientoReducido(reducido: boolean): void {
    this.movimientoReducido.set(reducido);
    window.localStorage.setItem(CLAVE_ALMACEN, String(reducido));
    document.documentElement.setAttribute('data-movimiento', reducido ? 'reducido' : 'normal');
  }
}
