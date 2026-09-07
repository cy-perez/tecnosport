import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { CheckoutStore } from '../../application/checkout.store';
import { CriteriosSeguimiento, usarSeguimientoPedido } from '../../application/seguimiento-pedido.consulta';
import { Pedido } from '../../domain/pedido.model';
import { datosTransferenciaDelPedido } from '../../domain/reglas-pedido';

/**
 * Paso 4e de `docs/09-plan-de-arranque.md`. Mismo patrón de doble fuente que
 * `EstadoPage` (paso 4f, construido justo antes): `CheckoutStore.pedido` si
 * el comprador nunca salió del sitio, o `GET /pedidos/{id}/seguimiento` con
 * `pedidoId`/`correo` de la URL si refrescó la página — copiar un número de
 * cuenta con calma es justo el tipo de pantalla en la que un refresh es
 * de esperar (`docs/11-pagos-y-envios.md`: "transferencia manual... reserva
 * de 24 horas, no de 30 minutos").
 */
@Component({
  selector: 'app-transferencia',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsPrecio],
  templateUrl: './transferencia.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferenciaPage {
  private readonly route = inject(ActivatedRoute);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly criteriosSeguimiento = computed<CriteriosSeguimiento | null>(() => {
    if (this.checkout.pedido() !== null) {
      return null;
    }
    const parametros = this.route.snapshot.queryParamMap;
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');
    return pedidoId && correo ? { pedidoId, correo } : null;
  });

  protected readonly consulta = usarSeguimientoPedido(() => this.criteriosSeguimiento());

  protected readonly pedido = computed<Pedido | null>(() => this.checkout.pedido() ?? this.consulta.data() ?? null);

  protected readonly datosTransferencia = computed(() => {
    const pedido = this.pedido();
    return pedido ? datosTransferenciaDelPedido(pedido) : null;
  });
}
