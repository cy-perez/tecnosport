import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { CarritoStore } from '../../../carrito/application/carrito.store';

/**
 * Nombra los productos que el comprador dejó marcados y ya no se venden: salen del pedido sin que
 * él los quitara, y que el total baje no le dice cuál. Lo usan el resumen y confirmar, que es donde
 * se ve el total antes de pagar. El producto sigue en el carrito.
 *
 * <p>Las desmarcadas no cuentan: esas ya las dejó fuera él. Y se calcula aquí y no en
 * `CarritoStore`, que va en el bundle inicial —lo usa el encabezado en todas las pantallas— y
 * ponerlo allá lo pasaba de su presupuesto de 700 kB; esto solo hace falta en el checkout.
 *
 * <p>`role="alert"` y dentro de un `@if`: puede aparecer con la pantalla abierta —la cotización
 * del carrito se refresca—, y una región asertiva se anuncia aunque nazca llena
 * (`apps/web/CLAUDE.md`).
 */
@Component({
  selector: 'app-aviso-fuera-del-pedido',
  imports: [TranslocoPipe],
  templateUrl: './aviso-fuera-del-pedido.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisoFueraDelPedido {
  private readonly carrito = inject(CarritoStore);
  private readonly traducir = usarTraductor();

  /** «Morral urbano, Gorra plana», o `null` si no salió nada. */
  protected readonly nombres = computed(() => {
    const lineas = (this.carrito.consulta.data()?.lineas ?? []).filter(
      (linea) => this.carrito.estaMarcada(linea.id) && !this.carrito.lineaSeVende(linea.id),
    );
    if (lineas.length === 0) {
      return null;
    }
    return lineas
      .map(
        (linea) =>
          this.carrito.snapshotDeLinea(linea.varianteId)?.nombreProducto ??
          this.traducir()('checkout.resumen.producto_sin_foto'),
      )
      .join(', ');
  });
}
