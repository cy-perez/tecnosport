import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../shared/ts-precio/ts-precio';
import { CarritoStore } from '../application/carrito.store';
import { LineaCarritoComponent } from './linea-carrito/linea-carrito';

@Component({
  selector: 'app-carrito',
  imports: [TranslocoPipe, RouterLink, TsBoton, TsEsqueleto, TsPrecio, LineaCarritoComponent],
  templateUrl: './carrito.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CarritoPage {
  protected readonly store = inject(CarritoStore);

  /**
   * La foto se resuelve aquí y no en la plantilla. Llamar a `snapshotDeLinea` desde el `@for`
   * parecía inocente y era una lectura de `localStorage` por línea **en cada ciclo de detección**;
   * un `computed` solo se recalcula cuando el carrito cambia de verdad.
   */
  protected readonly lineas = computed(() => {
    const carrito = this.store.consulta.data();
    if (!carrito) {
      return [];
    }
    return carrito.lineas.map((linea) => ({
      linea,
      snapshot: this.store.snapshotDeLinea(linea.varianteId),
    }));
  });

  protected readonly total = computed(() =>
    this.lineas().reduce(
      (suma, { linea, snapshot }) => suma + (snapshot ? snapshot.precioValor * linea.cantidad : 0),
      0,
    ),
  );

  protected cambiarCantidad(lineaId: string, cantidad: number): void {
    void this.store.actualizarCantidad(lineaId, cantidad);
  }

  protected eliminarLinea(lineaId: string): void {
    void this.store.eliminarLinea(lineaId);
  }
}
