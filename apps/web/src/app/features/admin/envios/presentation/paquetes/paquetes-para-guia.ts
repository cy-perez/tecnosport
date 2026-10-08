import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { TsPrecio } from '../../../../../shared/ts-precio/ts-precio';
import { usarPaquetesDePedido } from '../../application/paquetes-de-pedido.consulta';

/**
 * Los paquetes de un pedido tal como hay que escribirlos en "Cotizar y crear" de la plataforma,
 * mientras las guías se crean a mano (`ADR-0071`).
 *
 * <p>Existe porque hacerlo de cabeza sale mal justo donde más cuesta: en contraentrega, el valor
 * declarado de cada paquete lleva repartido el flete y la plataforma cobra en la puerta la suma.
 * Escribir "el total del pedido" en cada uno de dos paquetes cobra el doble. Aquí se copian los
 * números tal cual.
 *
 * <p>Vive en la fila expandida del pedido, como los paneles de retracto y garantía: se necesita con
 * el pedido delante.
 */
@Component({
  selector: 'app-paquetes-para-guia',
  imports: [TranslocoPipe, TsPrecio],
  templateUrl: './paquetes-para-guia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaquetesParaGuia {
  readonly pedidoId = input.required<string>();

  private readonly transloco = inject(TranslocoService);
  protected readonly consulta = usarPaquetesDePedido(() => this.pedidoId());

  /**
   * El motivo del 409, por su código: "un artículo no tiene medidas" pide medirlo o ponerle peso a
   * su categoría, y eso no se adivina con un "no se pudo".
   */
  protected readonly error = computed(() =>
    this.consulta.isError()
      ? mensajeDeError(this.consulta.error(), this.transloco, 'admin.paquetes_guia.error')
      : null,
  );
}
