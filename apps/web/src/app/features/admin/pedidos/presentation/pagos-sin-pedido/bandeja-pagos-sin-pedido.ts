import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsPrecio } from '../../../../../shared/ts-precio/ts-precio';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import {
  usarPagosSinPedido,
  usarReintegroDePagoSinPedido,
} from '../../application/pagos-sin-pedido.consulta';
import { MEDIO_POR_METODO, MEDIOS_DE_REINTEGRO } from '../../domain/medio-de-reintegro';
import { PagoSinPedidoAdmin } from '../../domain/pedido-admin.model';
import { CLAVE_MEDIO } from '../clave-medio';

interface FormularioReintegro {
  medio: FormControl<string>;
  comprobante: FormControl<string>;
}

/**
 * Los pagos que entraron sin un pedido que los esperara. Antes no existían para nadie: el pago
 * quedaba aprobado, el pedido no se tocaba y el dinero no se devolvía. Mientras haya alguno, esta
 * bandeja sale arriba de la lista de pedidos; vacía, no ocupa nada.
 */
@Component({
  selector: 'app-bandeja-pagos-sin-pedido',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsPrecio,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './bandeja-pagos-sin-pedido.html',
})
export class BandejaPagosSinPedido {
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarPagosSinPedido();
  protected readonly reintegro = usarReintegroDePagoSinPedido();

  private readonly formularios = new Map<string, FormGroup<FormularioReintegro>>();

  protected readonly pagos = computed<PagoSinPedidoAdmin[]>(() => this.consulta.data() ?? []);

  protected readonly opcionesMedio = computed<OpcionSelect[]>(() =>
    MEDIOS_DE_REINTEGRO.map((medio) => ({
      valor: medio,
      etiqueta: this.traducir()(CLAVE_MEDIO[medio]),
    })),
  );

  protected readonly error = computed(() => {
    const fallo = this.reintegro.error();
    return fallo ? mensajeDeError(fallo, this.transloco, 'admin.pedidos.acciones.error') : null;
  });

  protected formulario(pago: PagoSinPedidoAdmin): FormGroup<FormularioReintegro> {
    let form = this.formularios.get(pago.pagoId);
    if (!form) {
      form = new FormGroup({
        medio: new FormControl<string>(MEDIO_POR_METODO[pago.metodoPago], { nonNullable: true }),
        comprobante: new FormControl('', { nonNullable: true }),
      });
      this.formularios.set(pago.pagoId, form);
    }
    return form;
  }

  protected formatearFecha(iso: string): string {
    return fechaConHora(iso, this.transloco.activeLang());
  }

  protected registrando(pagoId: string): boolean {
    return this.reintegro.isPending() && this.reintegro.variables()?.pagoId === pagoId;
  }

  protected async registrar(pago: PagoSinPedidoAdmin): Promise<void> {
    if (this.reintegro.isPending()) {
      return;
    }
    const form = this.formulario(pago);
    const comprobante = form.controls.comprobante.value.trim();
    try {
      await this.reintegro.mutateAsync({
        pagoId: pago.pagoId,
        medio: form.controls.medio.value as MedioReintegro,
        comprobante: comprobante === '' ? null : comprobante,
      });
    } catch {
      // El mensaje lo pinta `error()`, en una región que se anuncia.
    }
  }
}
