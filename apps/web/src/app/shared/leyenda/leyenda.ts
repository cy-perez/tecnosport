import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Directive, ElementRef, inject, input, PLATFORM_ID } from '@angular/core';

/**
 * Un globo oscuro sobre el fondo, el inverso en cualquier tema: `texto` y `fondo` se intercambian
 * solos en el oscuro, así que el globo sigue contrastando sin una variante `oscuro:`.
 */
const CLASES_GLOBO =
  'pointer-events-none fixed inset-auto m-0 whitespace-nowrap rounded-sm border-0 bg-ts-texto px-8 py-4 font-texto text-xs text-ts-fondo shadow-md';
const ARRIBA = '-translate-y-4';
const ABAJO = 'translate-y-4';

/**
 * Una leyenda con un texto corto —el nombre de un color— al pasar el puntero sobre el elemento, o
 * al llegar a él con el teclado.
 *
 * <p><b>No es el `title` del navegador</b>: ese tarda en salir, no sale con el teclado, y el lector
 * de pantalla lo lee además del `aria-label`. Esta es solo visual (`aria-hidden`): el nombre
 * accesible lo sigue dando el `aria-label` del elemento, que ya lo trae.
 *
 * <p><b>Vive en la capa superior</b> (`popover`), fuera del elemento: la tarjeta de producto recorta
 * lo que se sale de ella, y una leyenda posicionada con CSS dentro de la tarjeta quedaba cortada en
 * las muestras de los bordes. Por eso se coloca midiendo: encima del elemento, centrada y sin
 * salirse de la ventana; debajo si arriba no cabe. Sin `popover` —jsdom— se muestra con `hidden`.
 *
 * <p>El globo existe solo mientras se ve: se crea al mostrarse y se quita al ocultarse. Conservarlo
 * dejaba un `<div>` en el `<body>` por cada muestra sobrevolada, cientos en una rejilla larga.
 *
 * <p>Un toque no la abre: el navegador emite el `pointerenter` del toque pero no el `pointerleave`
 * hasta el siguiente toque en otra parte, y el globo se quedaba encima de la tarjeta.
 */
@Directive({
  selector: '[appLeyenda]',
  host: {
    '(pointerenter)': 'alEntrar($event)',
    '(pointerleave)': 'ocultar()',
    '(focusin)': 'mostrarConTeclado()',
    '(focusout)': 'ocultar()',
  },
})
export class Leyenda {
  readonly texto = input.required<string>({ alias: 'appLeyenda' });

  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly documento = inject(DOCUMENT);
  private readonly enNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private globo: HTMLElement | null = null;

  /** Un desplazamiento deja el globo donde estaba el elemento: se quita en vez de perseguirlo. */
  private readonly alDesplazar = () => this.ocultar();

  /**
   * Escape en el documento y no en el elemento: abierta con el puntero, el foco puede estar en
   * cualquier otra parte, y la leyenda tiene que poder descartarse igual (WCAG 1.4.13).
   */
  private readonly alPulsar = (evento: KeyboardEvent) => {
    if (evento.key === 'Escape') {
      this.ocultar();
    }
  };

  constructor() {
    inject(DestroyRef).onDestroy(() => this.ocultar());
  }

  protected alEntrar(evento: PointerEvent): void {
    if (evento.pointerType !== 'touch') {
      this.mostrar();
    }
  }

  /** Con el teclado sí, con el clic no: un clic también enfoca, y el puntero ya está encima. */
  protected mostrarConTeclado(): void {
    if (this.elemento.matches(':focus-visible')) {
      this.mostrar();
    }
  }

  protected mostrar(): void {
    const texto = this.texto();
    if (!this.enNavegador || !texto) {
      return;
    }
    const globo = this.globo ?? this.crearGlobo();
    globo.textContent = texto;
    if (typeof globo.showPopover === 'function') {
      if (!globo.matches(':popover-open')) {
        globo.showPopover();
      }
    } else {
      globo.hidden = false;
    }
    this.colocar(globo);
    const ventana = this.documento.defaultView;
    ventana?.addEventListener('scroll', this.alDesplazar, { capture: true, passive: true });
    ventana?.addEventListener('keydown', this.alPulsar);
  }

  protected ocultar(): void {
    const ventana = this.documento.defaultView;
    ventana?.removeEventListener('scroll', this.alDesplazar, { capture: true });
    ventana?.removeEventListener('keydown', this.alPulsar);
    this.globo?.remove();
    this.globo = null;
  }

  private crearGlobo(): HTMLElement {
    const globo = this.documento.createElement('div');
    globo.className = CLASES_GLOBO;
    globo.setAttribute('aria-hidden', 'true');
    globo.setAttribute('data-leyenda', '');
    if (typeof globo.showPopover === 'function') {
      globo.setAttribute('popover', 'manual');
    } else {
      globo.hidden = true;
    }
    this.documento.body.appendChild(globo);
    this.globo = globo;
    return globo;
  }

  /**
   * Medidas del navegador, no valores de diseño: el `px` es su unidad. La separación del elemento
   * no se calcula aquí: la pone la traslación de la clase, que sale de los tokens.
   */
  private colocar(globo: HTMLElement): void {
    const caja = this.elemento.getBoundingClientRect();
    const ancho = globo.offsetWidth;
    const alto = globo.offsetHeight;
    const anchoVentana = this.documento.documentElement.clientWidth;
    const izquierda = Math.min(
      Math.max(caja.left + caja.width / 2 - ancho / 2, 0),
      Math.max(anchoVentana - ancho, 0),
    );
    // Dos altos y no uno: el del globo y holgura para la separación, que no se conoce en píxeles.
    const cabeArriba = caja.top >= alto * 2;
    globo.classList.toggle(ARRIBA, cabeArriba);
    globo.classList.toggle(ABAJO, !cabeArriba);
    globo.style.left = `${izquierda}px`;
    globo.style.top = `${cabeArriba ? caja.top - alto : caja.bottom}px`;
  }
}
