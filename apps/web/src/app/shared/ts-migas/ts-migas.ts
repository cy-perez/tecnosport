import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

export interface Miga {
  /** Ya traducida por quien llama: la miga no sabe de qué scope viene. */
  readonly etiqueta: string;
  /** Sin enlace = la página actual, el último eslabón de la ruta. */
  readonly enlace?: readonly (string | number)[];
}

/**
 * Ruta de navegación. Es una lista ordenada de verdad (`<ol>`), no una fila de
 * enlaces sueltos: el orden es la información.
 *
 * Los enlaces se reciben absolutos (con el prefijo de idioma incluido) porque
 * las migas se usan en pantallas a profundidades distintas del árbol de rutas y
 * un enlace relativo apuntaría a otro sitio en cada una — mismo motivo que
 * llevó a `ts-tarjeta-producto` a armar enlaces absolutos.
 */
@Component({
  selector: 'ts-migas',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './ts-migas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsMigas {
  readonly ruta = input.required<readonly Miga[]>();
}
