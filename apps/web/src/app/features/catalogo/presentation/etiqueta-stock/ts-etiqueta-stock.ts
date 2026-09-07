import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { cn } from '../../../../shared/ui/cn';

/**
 * Vive en `features/catalogo` y no en `shared/`, que es de donde vino.
 *
 * Nunca fue compartida: sus dos únicos consumidores son la ficha y la tarjeta
 * de producto, las dos del catálogo, y sus textos salen de `catalogo.*`, que es
 * un scope perezoso de esta funcionalidad. Un componente en `shared/` que usa
 * las claves de una funcionalidad concreta es la flecha al revés — y ESLint no
 * lo veía porque sus reglas de límites solo cubren `features/**`.
 */
@Component({
  selector: 'ts-etiqueta-stock',
  imports: [TranslocoPipe],
  templateUrl: './ts-etiqueta-stock.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsEtiquetaStock {
  readonly disponible = input.required<boolean>();

  /**
   * Agotado no se pinta en rojo a propósito: no es un error del visitante ni
   * un aviso, solo un estado. Va en el gris de texto suave.
   */
  protected readonly clases = computed(() =>
    cn(
      'inline-block bg-ts-superficie-alt px-8 py-4 text-xs font-medio',
      this.disponible() ? 'text-ts-exito' : 'text-ts-texto-suave',
    ),
  );
}
