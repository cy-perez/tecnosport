import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';

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
