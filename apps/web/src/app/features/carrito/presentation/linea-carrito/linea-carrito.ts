import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { LineaCarrito } from '../../domain/carrito.model';
import { SnapshotLinea } from '../../domain/snapshot-linea.model';

@Component({
  selector: 'app-linea-carrito',
  imports: [NgOptimizedImage, TranslocoPipe, TsBoton, TsPrecio],
  templateUrl: './linea-carrito.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineaCarritoComponent {
  readonly linea = input.required<LineaCarrito>();
  /** `null` si no hay foto guardada para esta variante (otro dispositivo, localStorage borrado). */
  readonly snapshot = input<SnapshotLinea | null>(null);
  readonly deshabilitado = input(false);

  readonly cantidadCambio = output<number>();
  readonly eliminar = output<void>();

  /**
   * El precio de hoy, del servidor (`CarritoStore.precioDeLinea`). `null` mientras llega o si la
   * variante ya no se vende. La foto guardada solo da el nombre y la imagen: su precio podía estar
   * viejo.
   */
  readonly precioUnitario = input<number | null>(null);

  protected readonly subtotal = computed(() => {
    const precio = this.precioUnitario();
    return precio === null ? null : precio * this.linea().cantidad;
  });

  protected restar(): void {
    if (this.linea().cantidad > 1) {
      this.cantidadCambio.emit(this.linea().cantidad - 1);
    }
  }

  protected sumar(): void {
    this.cantidadCambio.emit(this.linea().cantidad + 1);
  }
}
