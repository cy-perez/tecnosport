import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * La guía que se dibuja encima de la vista de cámara (`docs/10-captura-360.md`): marco de
 * proporción 1:1 —la proporción final—, cuadrícula de tercios, marca de centro y el **fantasma
 * del fotograma anterior**, que es la ayuda más útil de las tres: alinear contra la toma previa
 * es mucho más preciso que alinear contra una silueta genérica.
 *
 * La guía **se conserva idéntica entre tomas**: es lo que garantiza que el producto ocupe el
 * mismo espacio en todos los fotogramas.
 *
 * No hay silueta por categoría a propósito: dibujar un tenis, un morral y un celular es trabajo
 * de diseño que este componente no puede inventar. La cuadrícula sirve para lo mismo sin mentir.
 *
 * Es decorativa de punta a punta (`aria-hidden`): lo que hay que saber para capturar se dice en
 * texto, en el indicador de nivel y en el nombre de la toma.
 */
@Component({
  selector: 'ts-superposicion-guia',
  templateUrl: './ts-superposicion-guia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `class` reemplaza el `:host` del SCSS: la superposición se estira sobre la
  // vista de cámara y no debe interceptar ningún toque.
  host: { 'aria-hidden': 'true', class: 'pointer-events-none absolute inset-0 block' },
})
export class TsSuperposicionGuia {
  /** El fotograma anterior del set. Nulo en la primera toma, que no tiene contra qué alinearse. */
  readonly fantasma = input<string | null>(null);

  /** Permite apagar el fantasma sin perderlo, para ver la toma limpia un momento. */
  readonly mostrarFantasma = input(true);
}
