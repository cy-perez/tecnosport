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
