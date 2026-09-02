import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { hayExistencia, precioDesde, Producto } from '../../features/catalogo/domain/producto.model';
import { TsEtiquetaStock } from '../ts-etiqueta-stock/ts-etiqueta-stock';
import { TsPrecio } from '../ts-precio/ts-precio';

@Component({
  selector: 'ts-tarjeta-producto',
  imports: [NgOptimizedImage, TsPrecio, TsEtiquetaStock, RouterLink],
  templateUrl: './ts-tarjeta-producto.html',
  styleUrl: './ts-tarjeta-producto.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsTarjetaProducto {
  private readonly transloco = inject(TranslocoService);

  readonly producto = input.required<Producto>();

  protected readonly precio = computed(() => precioDesde(this.producto()));
  protected readonly disponible = computed(() => hayExistencia(this.producto()));

  protected readonly alt = computed(() => {
    const producto = this.producto();
    const imagen = producto.imagenPrincipal;
    if (!imagen) {
      return producto.nombre;
    }
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || producto.nombre;
  });
}
