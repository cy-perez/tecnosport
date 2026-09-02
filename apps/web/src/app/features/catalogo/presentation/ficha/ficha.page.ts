import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsEtiquetaStock } from '../../../../shared/ts-etiqueta-stock/ts-etiqueta-stock';
import { TsGaleria } from '../../../../shared/ts-galeria/ts-galeria';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { TsSelectorVariante } from '../../../../shared/ts-selector-variante/ts-selector-variante';
import { usarFichaProducto } from '../../application/buscar-ficha-producto.consulta';
import { Imagen } from '../../domain/producto.model';
import { ejesDeAtributos, Seleccion, seleccionDeVariante, variantePorDefecto, varianteSeleccionada } from '../../domain/seleccion-variante';

@Component({
  selector: 'app-ficha',
  imports: [TranslocoPipe, TsGaleria, TsSelectorVariante, TsPrecio, TsEtiquetaStock, TsEsqueleto, RouterLink],
  templateUrl: './ficha.page.html',
  styleUrl: './ficha.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FichaPage {
  private readonly route = inject(ActivatedRoute);

  private readonly slug = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  protected readonly consulta = usarFichaProducto(() => this.slug().get('slug') ?? '');

  protected readonly producto = computed(() => this.consulta.data());

  protected readonly imagenesGaleria = computed<Imagen[]>(() => {
    const producto = this.producto();
    if (!producto) {
      return [];
    }
    return [producto.imagenPrincipal, ...producto.galeria].filter((imagen): imagen is Imagen => imagen !== null);
  });

  protected readonly ejes = computed(() => {
    const producto = this.producto();
    return producto ? ejesDeAtributos(producto) : [];
  });

  protected readonly seleccion = signal<Seleccion>({});

  protected readonly varianteActiva = computed(() => {
    const producto = this.producto();
    return producto ? varianteSeleccionada(producto, this.seleccion()) : null;
  });

  constructor() {
    // Reinicia la selección a la variante por defecto cada vez que carga un producto distinto
    // (primer render, o al navegar de una ficha a otra).
    effect(() => {
      const producto = this.producto();
      if (!producto) {
        return;
      }
      const variante = variantePorDefecto(producto);
      this.seleccion.set(variante ? seleccionDeVariante(variante) : {});
    });
  }

  protected cambiarSeleccion(seleccion: Seleccion): void {
    this.seleccion.set(seleccion);
  }
}
