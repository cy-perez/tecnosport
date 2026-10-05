import { DOCUMENT, DestroyRef, Injector, afterNextRender, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * La pantalla, sin lo que no la cambia: el prefijo de idioma, los parámetros de consulta y el
 * fragmento. Dos URL con la misma pantalla son la misma página vista de otra forma —otro filtro,
 * otro orden, otro idioma— y ahí el foco no se mueve.
 */
export function pantallaDe(url: string): string {
  const ruta = url.split(/[?#]/)[0];
  const segmentos = ruta.split('/').filter((segmento) => segmento.length > 0);
  return '/' + segmentos.slice(1).join('/');
}

/**
 * Al cambiar de pantalla, el foco va al `<h1>` de la nueva —o a `<main id="contenido">` si no tiene—.
 *
 * <p>Una aplicación de una sola página no recarga el documento, así que el navegador no mueve el
 * foco: se queda en el enlace pulsado, que ya no existe, y cae en `<body>`. Quien usa lector de
 * pantalla no oye que llegó a otra página y quien navega con teclado vuelve a empezar desde el
 * encabezado. Llevarlo al título hace las dos cosas: lo anuncia y deja el Tab siguiente dentro del
 * contenido.
 *
 * <p><b>Solo cuando cambia la pantalla</b> (`pantallaDe`). Filtrar la rejilla o pedir "cargar más"
 * cambia los parámetros de consulta, y ahí arrancarle el foco a quien está usando un filtro sería
 * peor que no hacer nada. Tampoco al cambiar de idioma: lo hace el alternador, que deja el foco en
 * el segmento activo. Y nunca en la primera navegación, que es la carga de la página.
 *
 * <p>No se usa `withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })`: sube al principio
 * en <b>toda</b> navegación, también en las de parámetros de consulta, y cada filtro devolvería la
 * rejilla arriba. Enfocar el título ya la lleva a la vista cuando la pantalla cambia.
 */
export function usarFocoAlNavegar(): void {
  const router = inject(Router);
  const documento = inject(DOCUMENT);
  const injector = inject(Injector);
  let anterior: string | null = null;

  const suscripcion = router.events
    .pipe(filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd))
    .subscribe((evento) => {
      const actual = pantallaDe(evento.urlAfterRedirects);
      const cambio = anterior !== null && actual !== anterior;
      anterior = actual;
      if (!cambio) {
        return;
      }
      // `afterNextRender` y no un fotograma: `NavigationEnd` llega antes de que se pinte la
      // pantalla nueva, y hay que esperar a ese pintado para encontrar su título. No corre en el
      // servidor, que es justo lo que hace falta.
      afterNextRender(
        () => {
          const principal = documento.getElementById('contenido');
          const titulo = principal?.querySelector<HTMLElement>('h1');
          if (titulo && !titulo.hasAttribute('tabindex')) {
            titulo.setAttribute('tabindex', '-1');
          }
          (titulo ?? principal)?.focus();
        },
        { injector },
      );
    });
  inject(DestroyRef).onDestroy(() => suscripcion.unsubscribe());
}
