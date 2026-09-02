import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import {
  EjeAtributo,
  OpcionEje,
  Seleccion,
} from '../../features/catalogo/domain/seleccion-variante';
import { TsBoton } from '../ts-boton/ts-boton';

@Component({
  selector: 'ts-selector-variante',
  imports: [TsBoton],
  templateUrl: './ts-selector-variante.html',
  styleUrl: './ts-selector-variante.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorVariante {
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
