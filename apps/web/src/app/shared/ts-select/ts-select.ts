import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface OpcionSelect {
  readonly valor: string;
  readonly etiqueta: string;
}

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

@Component({
  selector: 'ts-select',
  templateUrl: './ts-select.html',
  styleUrl: './ts-select.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsSelect),
      multi: true,
    },
  ],
})
export class TsSelect implements ControlValueAccessor {
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
