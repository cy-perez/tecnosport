import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { EjeAtributo, OpcionEje, Seleccion } from '../../domain/seleccion-variante';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';

/**
 * El botón es el objetivo táctil; la muestra de color es lo que se ve. Iban
 * juntos —un botón de 32 x 32 pintado del color— y por eso el objetivo se
 * quedaba en 32, por debajo de los 44 px que pide `docs/04-ui-marca.md`.
 * Separarlos permite cumplir el objetivo sin agrandar la muestra.
 *
 * El botón no pinta nada: sin fondo ni borde, crecer a 44 no se nota.
 */
const SWATCH = 'anillo-foco grid size-tactil cursor-pointer place-items-center border-0 bg-transparent p-0';

const MUESTRA_BASE = 'block size-32';
const MUESTRA = `${MUESTRA_BASE} border border-ts-borde-control`;
const MUESTRA_ACTIVA = `${MUESTRA_BASE} border-2 border-ts-primario`;

@Component({
  selector: 'ts-selector-variante',
  imports: [TsBoton],
  templateUrl: './ts-selector-variante.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorVariante {
  /** Variantes completas: `border` y `border-2` compiten por la misma
   *  propiedad, así que no pueden convivir en el atributo. */
  protected claseSwatch(): string {
    return SWATCH;
  }

  protected claseMuestra(activa: boolean): string {
    return activa ? MUESTRA_ACTIVA : MUESTRA;
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
