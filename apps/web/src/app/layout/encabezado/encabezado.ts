import { ChangeDetectionStrategy, Component, DOCUMENT, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SesionStore } from '../../core/autenticacion/sesion.store';
import { CarritoStore } from '../../features/carrito/application/carrito.store';
import { iconoCarrito, iconoCerrar, iconoMenu } from '../../shared/ui/icono/iconos';
import { TsIcono } from '../../shared/ui/icono/ts-icono';
import { TsSelectorIdioma } from '../../shared/ts-selector-idioma/ts-selector-idioma';
import { TsSelectorTema } from '../../shared/ts-selector-tema/ts-selector-tema';

/**
 * Red de seguridad, no la duración de la animación: esa la decide el CSS
 * (`--animate-plegar`, 120 ms). Solo entra en juego si una animación no
 * resuelve nunca.
 */
const TIEMPO_MAXIMO_DE_SALIDA_MS = 400;

/**
 * El encabezado tiene dos formas y una sola fuente de contenido.
 *
 * Desde el primer punto de quiebre cabe todo en la barra. Por debajo, la barra
 * se queda con lo imprescindible —logo, carrito y el botón de menú— y el resto
 * baja a un panel desplegable. Los enlaces y los controles **no se escriben dos
 * veces**: viven en un `ng-template` y se instancian en los dos sitios con
 * `NgTemplateOutlet`. Duplicarlos sería garantizar que un día se actualice uno
 * y no el otro.
 *
 * El carrito se queda en la barra también en móvil, y es una decisión, no un
 * descuido: es la acción principal de una tienda y su contador es la única
 * señal de que hay algo dentro. `docs/04-ui-marca.md` no dice qué va en el menú
 * móvil, así que esto es criterio y se puede discutir.
 *
 * El panel es un *disclosure*, no un diálogo: no lleva trampa de foco ni
 * `aria-modal`. El contenido de detrás no queda inerte —se puede seguir
 * tabulando al pie— y eso es correcto para un menú que no bloquea la página.
 * Si algún día tapa la pantalla completa, entonces sí toca `ts-dialogo`.
 */
@Component({
  selector: 'app-encabezado',
  imports: [TranslocoPipe, NgTemplateOutlet, RouterLink, TsIcono, TsSelectorIdioma, TsSelectorTema],
  templateUrl: './encabezado.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // El `relative` que ancla el panel desplegable va en el `<header>` de la
  // plantilla, no aquí: el landmark es el elemento, no el host del componente.
  //
  // Escape se escucha en el host y no en un `div` de la plantilla: un `div`
  // con manejador tendría que ser enfocable para ser operable —lo exige
  // `interactive-supports-focus` y tiene razón—, y hacer enfocable un
  // contenedor que no es un control es peor que no tenerlo. En el host, el
  // evento llega por burbujeo desde cualquier hijo, que es exactamente lo que
  // hace falta: el foco está en el botón o dentro del panel.
  host: { class: 'block', '(keydown.escape)': 'cerrarMenu()' },
})
export class Encabezado {
  private readonly documento = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly carrito = inject(CarritoStore);
  protected readonly sesion = inject(SesionStore);

  protected readonly idiomaActual = this.transloco.activeLang;

  protected readonly iconoCarrito = iconoCarrito;
  protected readonly iconoMenu = iconoMenu;
  protected readonly iconoCerrar = iconoCerrar;

  protected readonly menuAbierto = signal(false);

  /**
   * Las dos vías del proyecto: la preferencia del sistema operativo y el
   * control del pie, que escribe `data-movimiento` en `<html>`.
   */
  private prefiereMenosMovimiento(): boolean {
    const documento = this.documento;
    return (
      documento.documentElement.getAttribute('data-movimiento') === 'reducido' ||
      (documento.defaultView?.matchMedia('(prefers-reduced-motion: reduce)').matches ?? false)
    );
  }

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  /**
   * Se llama desde el propio panel por delegación de eventos: cualquier clic
   * dentro lo cierra, que es lo que se espera al elegir un enlace. También lo
   * llama Escape. No hace falta escuchar la navegación del router: si el clic
   * no navega, cerrar el menú tampoco molesta.
   */
  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  /**
   * Anima la salida del panel y **solo entonces** lo deja eliminar.
   *
   * Se usa la forma de evento de `animate.leave` y no la de clase. Con la de
   * clase, Angular espera una señal de fin que en este montaje nunca llegaba:
   * el panel se quedaba en el DOM, visible, después de cerrarlo. Aquí la señal
   * la damos nosotros llamando a `animationComplete()`.
   *
   * Se espera a las animaciones reales del elemento (`getAnimations`), no a un
   * temporizador que adivine la duración. Y hay dos redes por debajo, porque
   * **un panel que no se elimina es mucho peor que uno sin animación**: si no
   * hay ninguna animación corriendo —movimiento reducido, o jsdom, que no
   * implementa la API— se completa al instante; y si alguna nunca resuelve,
   * un tiempo máximo lo desbloquea igual.
   */
  protected alSalirElMenu(evento: { target: Element; animationComplete: () => void }): void {
    const { target, animationComplete } = evento;

    // Quien pide menos movimiento no quiere una animación de 0,01 ms: quiere
    // ninguna. Reducir la duración a casi cero dejaba una animación real
    // corriendo, y con ciclos rápidos de abrir y cerrar el panel llegó a
    // quedarse montado — comprobado en el navegador. Aquí se sale por lo
    // directo: sin clase, sin espera, sin carrera.
    if (this.prefiereMenosMovimiento()) {
      animationComplete();
      return;
    }

    target.classList.add('animate-plegar');

    let terminado = false;
    const terminar = (): void => {
      if (terminado) {
        return;
      }
      terminado = true;
      animationComplete();
    };

    const animaciones = target.getAnimations?.() ?? [];
    if (animaciones.length === 0) {
      terminar();
      return;
    }

    void Promise.all(animaciones.map((a) => a.finished)).then(terminar, terminar);
    setTimeout(terminar, TIEMPO_MAXIMO_DE_SALIDA_MS);
  }

  // Un fallo de red al cerrar sesión no puede dejar al visitante atrapado en
  // el encabezado: se navega igual. El access token vive solo en memoria, así
  // que recargar ya lo deja sin sesión.
  protected async cerrarSesion(): Promise<void> {
    this.cerrarMenu();
    await this.sesion.cerrarSesion().catch(() => undefined);
    void this.router.navigate(['/', this.idiomaActual()]);
  }
}
