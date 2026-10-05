import { DOCUMENT } from '@angular/common';
import { afterNextRender, DestroyRef, Directive, ElementRef, inject, input } from '@angular/core';

/**
 * Publica el alto real del elemento en una propiedad personalizada de `<html>`, y la retira al
 * destruirse, para que una regla de CSS fuera del componente pueda contar con ese alto.
 *
 * Existe por la barra de compra de la ficha. `src/tailwind.css` reserva su alto al pie del
 * documento (`scroll-padding-bottom`) para que un control enfocado no quede debajo de ella, y lo
 * calculaba con una fórmula de una sola fila. Pero la barra se parte según el ancho, el idioma, lo
 * largo del precio y el mensaje de resultado: a 390 px medía 128 y la fórmula daba 84 (medido en dev
 * el 4 de octubre de 2026). Ninguna fórmula fija acierta en todos los casos, y la medida sí.
 *
 * `afterNextRender` es la guardia de plataforma: en el servidor no corre. Sin `ResizeObserver` no
 * se publica nada, y la regla de CSS usa su fórmula de respaldo, que es el comportamiento anterior.
 */
@Directive({ selector: '[appAltoEnVariable]' })
export class AltoEnVariable {
  /** El nombre de la propiedad, con sus dos guiones: `--alto-barra-compra`. */
  readonly variable = input.required<string>({ alias: 'appAltoEnVariable' });

  constructor() {
    const elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const documento = inject(DOCUMENT);
    const destruccion = inject(DestroyRef);

    afterNextRender(() => {
      const ventana = documento.defaultView;
      if (!ventana || typeof ventana.ResizeObserver !== 'function') {
        return;
      }
      const raiz = documento.documentElement;
      const nombre = this.variable();
      const observador = new ventana.ResizeObserver(() => {
        // Una medida del navegador, no un valor de diseño: el `px` es su unidad.
        raiz.style.setProperty(nombre, `${Math.ceil(elemento.getBoundingClientRect().height)}px`);
      });
      observador.observe(elemento);
      destruccion.onDestroy(() => {
        observador.disconnect();
        raiz.style.removeProperty(nombre);
      });
    });
  }
}
