import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MetodoPago } from '../../domain/pedido.model';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';

export interface OpcionMetodoPago {
  readonly valor: MetodoPago;
  readonly etiqueta: string;

  /**
   * Lo que el botón no puede decir con su nombre, debajo y en letra menor. Opcional porque no
   * todos lo necesitan: "Sistecrédito" y "Pago contraentrega" se explican solos.
   *
   * <p>Nació con el agrupamiento de los medios de Wompi (28 de septiembre de 2026). Un botón que
   * dijera solo "Wompi" sería peor que los cuatro que reemplaza: nombra a la pasarela, que al
   * comprador no le dice nada, en vez del medio, que es lo que él reconoce. La descripción es la
   * que devuelve esa información — sin volver a prometer cuál se usará, que es lo que no se puede
   * prometer.
   */
  readonly detalle?: string;
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
