import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsPrecio } from '../../../../../shared/ts-precio/ts-precio';
import {
  usarPagosSinTransaccion,
  usarPedidosDetenidosEnPreparacion,
  usarRevisionDePagoSinTransaccion,
} from '../../application/alertas-de-pedidos.consulta';
import { PagoSinTransaccionAdmin, PedidoDetenidoAdmin } from '../../domain/pedido-admin.model';

/**
 * Lo que ningún proceso automático resuelve y alguien tiene que mirar. Mismo patrón que la bandeja
 * de pagos sin pedido: sale arriba de la lista mientras haya algo, y vacía no ocupa nada.
 *
 * Los pagos de Wompi pendientes y sin id de transacción no los concilia nadie —la API de Wompi
 * consulta por su id, no por la referencia—, así que la única salida es buscarlos a mano en el
 * portal de Wompi. Casi todos serán checkouts abandonados; se marcan como revisados y dejan de
 * salir.
 *
 * Los pedidos detenidos en preparación tampoco los mira nadie: uno de recogida o de contraentrega
 * conserva su reserva sin vencimiento, y los vigilantes empiezan en otros estados. Solo se avisan;
 * la salida es despacharlos, marcarlos listos o cancelarlos desde la lista de abajo.
 */
@Component({
  selector: 'app-alertas-de-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslocoPipe, TsBoton, TsPrecio],
  templateUrl: './alertas-de-pedidos.html',
})
export class AlertasDePedidos {
  private readonly transloco = inject(TranslocoService);

  protected readonly pagosSinTransaccion = usarPagosSinTransaccion();
  protected readonly revision = usarRevisionDePagoSinTransaccion();

  protected readonly pagos = computed<readonly PagoSinTransaccionAdmin[]>(
    () => this.pagosSinTransaccion.data()?.pagos ?? [],
  );
  protected readonly umbralHoras = computed(() => this.pagosSinTransaccion.data()?.umbralHoras);

  protected readonly detenidosEnPreparacion = usarPedidosDetenidosEnPreparacion();
  protected readonly detenidos = computed<readonly PedidoDetenidoAdmin[]>(
    () => this.detenidosEnPreparacion.data()?.pedidos ?? [],
  );
  protected readonly umbralDias = computed(() => this.detenidosEnPreparacion.data()?.umbralDias);

  /** La contraentrega se nombra aparte: es la que no cobró nada y aun así aparta la unidad. */
  protected claveEntrega(pedido: PedidoDetenidoAdmin): string {
    if (pedido.metodoPago === 'CONTRAENTREGA') {
      return 'admin.pedidos.alertas.en_preparacion.entrega_contraentrega';
    }
    return pedido.tipoEntrega === 'RETIRO_EN_PUNTO'
      ? 'admin.pedidos.alertas.en_preparacion.entrega_recogida'
      : 'admin.pedidos.alertas.en_preparacion.entrega_domicilio';
  }

  protected readonly error = computed(() => {
    const fallo = this.revision.error();
    return fallo ? mensajeDeError(fallo, this.transloco, 'admin.pedidos.acciones.error') : null;
  });

  protected formatearFecha(iso: string): string {
    return fechaConHora(iso, this.transloco.activeLang());
  }

  protected revisando(pagoId: string): boolean {
    return this.revision.isPending() && this.revision.variables() === pagoId;
  }

  protected async marcarRevisado(pago: PagoSinTransaccionAdmin): Promise<void> {
    if (this.revision.isPending()) {
      return;
    }
    try {
      await this.revision.mutateAsync(pago.pagoId);
    } catch {
      // El mensaje lo pinta `error()`, en una región que se anuncia.
    }
  }
}
