import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsEtiquetaStock } from '../../../../shared/ts-etiqueta-stock/ts-etiqueta-stock';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { usarFichaProducto } from '../../application/buscar-ficha-producto.consulta';
import { hayExistencia, precioDesde } from '../../domain/producto.model';

@Component({
  selector: 'app-ficha',
  imports: [TranslocoPipe, NgOptimizedImage, TsPrecio, TsEtiquetaStock, TsEsqueleto, RouterLink],
  templateUrl: './ficha.page.html',
  styleUrl: './ficha.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPage {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);

  private readonly slug = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  protected readonly consulta = usarFichaProducto(() => this.slug().get('slug') ?? '');

  protected readonly producto = computed(() => this.consulta.data());
  protected readonly precio = computed(() => {
    const producto = this.producto();
    return producto ? precioDesde(producto) : null;
  });
  protected readonly disponible = computed(() => {
    const producto = this.producto();
    return producto ? hayExistencia(producto) : false;
  });

  protected readonly alt = computed(() => {
    const producto = this.producto();
    const imagen = producto?.imagenPrincipal;
    if (!producto || !imagen) {
      return producto?.nombre ?? '';
    }
    const idioma = this.transloco.activeLang();
    return (idioma === 'en' ? imagen.altEn : imagen.altEs) || producto.nombre;
  });
}
