import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { cn } from '../cn';

/**
 * El cascarón de una pantalla de formulario: ancho de columna, aire vertical y
 * titular.
 *
 * Existe porque estaba repetido ocho veces en cinco pantallas de `cuenta` —las
 * que tienen estado de éxito lo repiten dentro de la misma plantilla— y otras
 * cuatro veces en `admin`. Doce copias de las mismas dos líneas es la forma
 * segura de que un día dejen de coincidir.
 *
 * Es tonto: el título llega **ya traducido**, así que no depende de Transloco
 * ni de ningún scope.
 */
@Component({
  selector: 'ts-pagina-formulario',
  templateUrl: './ts-pagina-formulario.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
})
export class TsPaginaFormulario {
  readonly titulo = input.required<string>();

  /** `ancho` para los formularios largos, como agregar una variante. */
  readonly ancho = input<'normal' | 'ancho'>('normal');

  /** Ajustes puntuales de quien llama, p. ej. `text-center`. */
  readonly clase = input('');

  protected readonly clases = computed(() =>
    cn(
      'mx-auto my-32 px-16',
      this.ancho() === 'ancho' ? 'max-w-formulario-lg' : 'max-w-formulario',
      this.clase(),
    ),
  );
}
