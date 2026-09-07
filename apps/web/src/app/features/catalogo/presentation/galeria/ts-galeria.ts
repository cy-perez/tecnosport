import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Imagen, urlPreferida } from '../../domain/producto.model';

const MINIATURA_BASE =
  'anillo-foco relative size-64 cursor-pointer overflow-hidden bg-transparent p-0';
const MINIATURA = `${MINIATURA_BASE} border border-ts-borde`;
const MINIATURA_ACTIVA = `${MINIATURA_BASE} border-2 border-ts-primario`;

@Component({
  selector: 'ts-galeria',
  imports: [NgOptimizedImage],
  templateUrl: './ts-galeria.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsGaleria {
  /**
   * Las dos variantes completas, no una base más un `[class.x]`. `border` y
   * `border-2` tocan la misma propiedad: dejarlas juntas en el atributo hace
   * que gane el orden del CSS compilado y no la condición. Y son constantes,
   * así que no pasa por `cn()` en cada ciclo de detección.
   */
  protected claseMiniatura(activa: boolean): string {
    return activa ? MINIATURA_ACTIVA : MINIATURA;
  }

  private readonly transloco = inject(TranslocoService);

  readonly imagenes = input.required<readonly Imagen[]>();

  /**
   * Quién es la candidata a LCP lo sabe la pantalla, no el componente — mismo criterio que
   * `ts-tarjeta-producto`. En una ficha con visor 360 la prioritaria es el fotograma frontal del
   * visor, y esta deja de serlo: priorizar las dos es no priorizar ninguna.
   *
   * **Por omisión, `false`.** El valor por defecto tiene que ser el que no hace daño: una pantalla
   * nueva que se olvide de decidir se lleva una imagen sin priorizar, no una segunda candidata a
   * LCP compitiendo con la de verdad (`NG02955`, que este proyecto ya pagó dos veces).
   *
   * `priority` es una de las entradas que NgOptimizedImage congela tras inicializar, así que quien
   * la use tiene que pasar un valor fijo por instancia, no una expresión que cambie.
   */
  readonly prioritaria = input(false);

  protected readonly indiceActivo = signal(0);

  protected readonly activa = computed<Imagen | null>(
    () => this.imagenes()[this.indiceActivo()] ?? null,
  );

  protected elegir(indice: number): void {
    this.indiceActivo.set(indice);
  }

  /** La misma regla que el visor 360: WebP con el original de respaldo. */
  protected readonly url = urlPreferida;

  protected alt(imagen: Imagen): string {
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || '';
  }
}
