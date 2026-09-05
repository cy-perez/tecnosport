import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TipoCampo = 'text' | 'number' | 'search' | 'password' | 'email';

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

@Component({
  selector: 'ts-campo',
  templateUrl: './ts-campo.html',
  styleUrl: './ts-campo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsCampo),
      multi: true,
    },
  ],
})
export class TsCampo implements ControlValueAccessor {
  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly tipo = input<TipoCampo>('text');
  readonly error = input<string | null>(null);

  protected readonly valorMostrado = signal('');
  protected readonly deshabilitado = signal(false);

  private alCambiar: (valor: string | number | null) => void = sinOperacion;
  private alTocar: () => void = sinOperacion;

  writeValue(valor: string | number | null): void {
    this.valorMostrado.set(valor === null || valor === undefined ? '' : String(valor));
  }

  registerOnChange(fn: (valor: string | number | null) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.deshabilitado.set(deshabilitado);
  }

  protected manejarEntrada(valorCrudo: string): void {
    this.valorMostrado.set(valorCrudo);
    this.alCambiar(this.tipo() === 'number' ? (valorCrudo === '' ? null : Number(valorCrudo)) : valorCrudo);
  }

  protected manejarSalida(): void {
    this.alTocar();
  }
}
