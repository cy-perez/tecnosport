import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MetodoPago } from '../../domain/pedido.model';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';

export interface OpcionMetodoPago {
  readonly valor: MetodoPago;
  readonly etiqueta: string;
}

/** Mismo patrón de "botones de alternancia" que `ts-selector-variante`: no es
 * un `ControlValueAccessor` porque no hay ningún `FormGroup` de la Fase 3 que
 * necesite bindearlo con `formControlName` — quien lo usa maneja el valor
 * elegido como una señal propia. */
@Component({
  selector: 'ts-selector-metodo-pago',
  imports: [TsBoton],
  templateUrl: './ts-selector-metodo-pago.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorMetodoPago {
  readonly opciones = input.required<readonly OpcionMetodoPago[]>();
  readonly seleccionado = input<MetodoPago | null>(null);
  readonly etiquetaGrupo = input.required<string>();

  readonly seleccionCambio = output<MetodoPago>();

  protected estaSeleccionado(opcion: OpcionMetodoPago): boolean {
    return this.seleccionado() === opcion.valor;
  }

  protected elegir(opcion: OpcionMetodoPago): void {
    this.seleccionCambio.emit(opcion.valor);
  }
}
