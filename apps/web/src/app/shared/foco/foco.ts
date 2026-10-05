import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectorRef,
  ElementRef,
  inject,
  Injector,
  PLATFORM_ID,
} from '@angular/core';

/**
 * Llevar el foco al primer campo inválido del componente, después de que se pinten los errores.
 *
 * Un formulario que falla al enviar marca todo como tocado y pinta los `[error]`, pero el foco se
 * queda en el botón de enviar: quien usa lector de pantalla oye "Continuar" y nada más, y tiene
 * que recorrer el formulario entero para encontrar qué falló. Se busca `[aria-invalid="true"]`
 * —lo ponen `ts-campo`, `ts-select`, `ts-area-texto` y `ts-checkbox` cuando tienen error— dentro
 * del propio componente.
 *
 * `afterNextRender` y no `requestAnimationFrame`: el `aria-invalid` aparece en el repintado que
 * provoca `markAllAsTouched()`, y hay que esperar a ese repintado y no a un fotograma cualquiera.
 * El `markForCheck()` garantiza que ese repintado ocurra aunque los errores ya estuvieran a la
 * vista; sin él, el foco saltaría en el siguiente repintado, que puede llegar mucho después.
 */
export function usarFocoEnPrimerInvalido(): () => void {
  const injector = inject(Injector);
  const anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  const deteccion = inject(ChangeDetectorRef);
  return () => {
    deteccion.markForCheck();
    afterNextRender(
      () => anfitrion.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
      { injector },
    );
  };
}

/**
 * Mover el foco después de que Angular haya repintado.
 *
 * Existe porque el panel tiene cuatro pantallas con la misma interacción —un botón de fila abre
 * una confirmación o un formulario, y al confirmar o cancelar esa caja desaparece con el botón
 * dentro— y el navegador, cuando el elemento enfocado deja de existir, manda el foco a `<body>`:
 * quien navega con teclado vuelve al principio del documento sin ninguna pista de qué pasó.
 *
 * `editar` ya lo resolvía con este mismo `requestAnimationFrame`; las otras tres copiaron la
 * interacción y no el arreglo. Vive aquí para que la próxima pantalla que copie la interacción
 * tenga a mano la solución y no el defecto.
 *
 * La guarda de plataforma no es decorativa: en SSR no hay `requestAnimationFrame`, y en una
 * pestaña oculta no se ejecuta nunca — comprobar el foco ahí da falso negativo siempre.
 */
export function usarFoco(): (elemento: () => HTMLElement | null | undefined) => void {
  const esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  return (elemento) => {
    if (!esNavegador) {
      return;
    }
    requestAnimationFrame(() => elemento()?.focus());
  };
}
