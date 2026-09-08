import { computed, inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { filter } from 'rxjs';

type Parametros = Record<string, unknown>;

/**
 * Traductor para leer una clave DENTRO de un `computed`.
 *
 * `transloco.translate()` llamado directo en un `computed` (o en un
 * inicializador de campo) no es reactivo: no lee ninguna señal, así que el
 * `computed` se evalúa una sola vez y jamás se recalcula. Si en ese momento el
 * scope perezoso todavía no cargó, la clave cruda se queda en pantalla para
 * siempre; y si el visitante cambia de idioma sin que Angular recree el
 * componente, la etiqueta se queda en el idioma anterior.
 *
 * Este traductor cuelga de `events$`, que emite tanto al cargar un scope
 * (`translationLoadSuccess`) como al cambiar de idioma (`langChanged`), así que
 * leerlo dentro de un `computed` crea la dependencia que faltaba.
 *
 * Va de la mano con `precargarScopeI18n` en el `resolve` de la ruta: el
 * traductor garantiza que la etiqueta se corrija, la precarga garantiza que
 * nunca se vea mal ni por un instante.
 */
export function usarTraductor(): Signal<(clave: string, parametros?: Parametros) => string> {
  const transloco = inject(TranslocoService);

  const revision = toSignal(
    transloco.events$.pipe(
      filter((evento) => evento.type === 'translationLoadSuccess' || evento.type === 'langChanged'),
    ),
    { initialValue: null },
  );

  return computed(() => {
    revision();
    return (clave: string, parametros?: Parametros) => transloco.translate(clave, parametros);
  });
}

/**
 * Lo mismo que {@link usarTraductor}, pero para leer una **estructura** y no una cadena: el texto
 * de las páginas legales vive en el scope como una lista de secciones, y lo que hace falta traer no
 * es un título sino el documento entero.
 *
 * Existe aparte de `translateObjectSignal` porque aquel resuelve una clave fija, y aquí la clave
 * depende de qué documento se esté viendo. Cuelga del mismo `events$` por el mismo motivo, así que
 * la trampa del `computed` que no se recalcula está cubierta igual.
 */
export function usarTraductorDeObjetos(): Signal<<T>(clave: string) => T | undefined> {
  const transloco = inject(TranslocoService);

  const revision = toSignal(
    transloco.events$.pipe(
      filter((evento) => evento.type === 'translationLoadSuccess' || evento.type === 'langChanged'),
    ),
    { initialValue: null },
  );

  return computed(() => {
    revision();
    return <T,>(clave: string): T | undefined => transloco.translateObject<T>(clave);
  });
}
