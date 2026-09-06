import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Imagen } from '../../features/catalogo/domain/producto.model';

@Component({
  selector: 'ts-galeria',
  imports: [NgOptimizedImage],
  templateUrl: './ts-galeria.html',
  styleUrl: './ts-galeria.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsGaleria {
  private readonly transloco = inject(TranslocoService);

  readonly imagenes = input.required<readonly Imagen[]>();

  /**
   * Quién es la candidata a LCP lo sabe la pantalla, no el componente — mismo criterio que
   * `ts-tarjeta-producto`. En una ficha con visor 360 la prioritaria es el fotograma frontal del
   * visor, y esta deja de serlo: priorizar las dos es no priorizar ninguna.
   *
   * `priority` es una de las entradas que NgOptimizedImage congela tras inicializar, así que quien
   * la use tiene que pasar un valor fijo por instancia, no una expresión que cambie.
   */
  readonly prioritaria = input(true);

  protected readonly indiceActivo = signal(0);

  protected readonly activa = computed<Imagen | null>(() => this.imagenes()[this.indiceActivo()] ?? null);

  protected elegir(indice: number): void {
    this.indiceActivo.set(indice);
  }

  protected alt(imagen: Imagen): string {
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || '';
  }
}
