import { DOCUMENT, DestroyRef, Injectable, afterNextRender, inject, signal } from '@angular/core';

const CONSULTA = '(prefers-reduced-motion: reduce)';

/**
 * ¿Quien mira pidió menos movimiento? Una sola fuente para el sitio entero, y **viva**.
 *
 * <p>Hasta el 4 de octubre de 2026 la pregunta se hacía en tres sitios —el encabezado, el menú
 * lateral y el carrusel de portada— y los tres la hacían mal de la misma forma: leían
 * `prefers-reduced-motion` <b>una sola vez</b>. Quien activaba "reducir movimiento" en el sistema
 * con la pestaña abierta seguía viendo el carrusel rotar hasta recargar. Aquí se escucha el
 * `change` de la consulta y la señal se corrige sola.
 *
 * <p>Los tres leían además el atributo `data-movimiento` de `<html>`, que **no escribe nadie**
 * desde que se quitó la casilla del pie el 25 de septiembre de 2026 (`layout/pie/pie.ts`). Eran
 * lecturas muertas, y se fueron. Los ganchos de CSS `[data-movimiento='reducido']` siguen en
 * `tailwind.css` y `styles.scss`: no cuestan nada y son lo que haría falta si el control vuelve; el
 * día que vuelva, el sitio de leerlo desde TypeScript es este.
 *
 * <p>Arranca en `false` y se rellena en `afterNextRender`: en el servidor no hay `matchMedia`, y el
 * valor inicial tiene que ser el que no rompe nada si nadie lo corrige.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciaDeMovimiento {
  private readonly documento = inject(DOCUMENT);

  private readonly reducidoInterno = signal(false);

  /** `true` si el sistema pide menos movimiento. Cambia sola si la persona cambia la preferencia. */
  readonly reducido = this.reducidoInterno.asReadonly();

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const consulta = this.documento.defaultView?.matchMedia?.(CONSULTA);
      if (!consulta) {
        return;
      }
      this.reducidoInterno.set(consulta.matches);
      const alCambiar = (evento: MediaQueryListEvent): void =>
        this.reducidoInterno.set(evento.matches);
      consulta.addEventListener('change', alCambiar);
      destroyRef.onDestroy(() => consulta.removeEventListener('change', alCambiar));
    });
  }
}
