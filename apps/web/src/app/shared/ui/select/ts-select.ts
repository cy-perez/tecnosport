import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';

export interface OpcionSelect {
  readonly valor: string;
  readonly etiqueta: string;
}

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

@Component({
  selector: 'ts-select',
  templateUrl: './ts-select.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `min-w-0` en el host, y no es un detalle. Un ítem de grid o de flex
  // arranca con `min-width: auto`, así que no puede encogerse por debajo del
  // ancho mínimo de su contenido; un `<select>` mide por su opción más larga,
  // y un texto largo ensanchaba su columna comprimiendo las demás — por eso
  // los filtros del catálogo se veían de tamaños distintos.
  host: { class: 'block min-w-0' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsSelect),
      multi: true,
    },
  ],
})
export class TsSelect implements ControlValueAccessor {
  protected readonly clasesControl = CLASES_CONTROL;
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesError = CLASES_ERROR;

  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly opciones = input.required<readonly OpcionSelect[]>();
  /** Texto de la opción vacía, para un select que representa "sin filtro". */
  readonly placeholder = input<string | null>(null);
  readonly error = input<string | null>(null);

  protected readonly valor = signal('');
  protected readonly deshabilitado = signal(false);

  private alCambiar: (valor: string) => void = sinOperacion;
  private alTocar: () => void = sinOperacion;

  writeValue(valor: string | null): void {
    this.valor.set(valor ?? '');
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.deshabilitado.set(deshabilitado);
  }

  protected manejarCambio(valor: string): void {
    this.valor.set(valor);
    this.alCambiar(valor);
    this.alTocar();
  }
}
