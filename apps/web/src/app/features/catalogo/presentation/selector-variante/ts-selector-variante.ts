import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { EjeAtributo, OpcionEje, Seleccion } from '../../domain/seleccion-variante';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';

const SWATCH_BASE = 'anillo-foco size-32 cursor-pointer p-0';
const SWATCH = `${SWATCH_BASE} border border-ts-borde-control`;
const SWATCH_ACTIVA = `${SWATCH_BASE} border-2 border-ts-primario`;

@Component({
  selector: 'ts-selector-variante',
  imports: [TsBoton],
  templateUrl: './ts-selector-variante.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorVariante {
  /** Variantes completas: `border` y `border-2` compiten por la misma
   *  propiedad, así que no pueden convivir en el atributo. */
  protected claseSwatch(activa: boolean): string {
    return activa ? SWATCH_ACTIVA : SWATCH;
  }

  readonly ejes = input.required<readonly EjeAtributo[]>();
  readonly seleccion = input.required<Seleccion>();
  readonly seleccionCambio = output<Seleccion>();

  protected esColor(opcion: OpcionEje): boolean {
    return opcion.colorHex !== null;
  }

  protected estaSeleccionada(nombreEje: string, opcion: OpcionEje): boolean {
    return this.seleccion()[nombreEje] === opcion.valor;
  }

  protected elegir(nombreEje: string, opcion: OpcionEje): void {
    this.seleccionCambio.emit({ ...this.seleccion(), [nombreEje]: opcion.valor });
  }
}
