import { Directive, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TsSelect } from './ts-select';

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

/**
 * El puente entre `ts-select` y los formularios de Angular, en una directiva y no dentro del
 * componente: así `@angular/forms` viaja solo a las pantallas que tienen formularios. El
 * encabezado usa el mismo `ts-select` sin arrastrarlo (ver el comentario de `TsSelect`).
 *
 * Se aplica sola, por el selector, en cuanto alguien pone `formControl`, `formControlName` o
 * `ngModel` sobre un `ts-select` — pero hay que importarla en el componente, junto a
 * `ReactiveFormsModule`. Sin importarla, Angular no encuentra accesor y falla en tiempo de
 * ejecución con NG01203, no en compilación.
 *
 * No hace falta desuscribirse de `cambio`: la directiva y el componente viven en el mismo
 * elemento y se destruyen juntos.
 */
@Directive({
  // La regla de ESLint exige que el atributo del selector lleve el prefijo del proyecto, y aquí
  // no puede: los atributos son `formControl`, `formControlName` y `ngModel`, que los pone
  // Angular. Es la misma forma de selector que usan los accesores del propio framework
  // (`DefaultValueAccessor` es `select:not([multiple])[formControlName]…`). Pedir además un
  // atributo propio dejaría un NG01203 en tiempo de ejecución esperando a quien lo olvide.
  // eslint-disable-next-line @angular-eslint/directive-selector -- ver arriba
  selector: 'ts-select[formControl], ts-select[formControlName], ts-select[ngModel]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsSelectControl),
      multi: true,
    },
  ],
})
export class TsSelectControl implements ControlValueAccessor {
  private readonly select = inject(TsSelect, { self: true });

  private alCambiar: (valor: string) => void = sinOperacion;
  private alTocar: () => void = sinOperacion;

  constructor() {
    // `cambio` y no el modelo `valor`: este solo emite cuando el usuario elige, que es justo
    // cuando un formulario debe darse por tocado y sucio.
    this.select.cambio.subscribe((valor) => {
      this.alCambiar(valor);
      this.alTocar();
    });
  }

  writeValue(valor: string | null): void {
    this.select.valor.set(valor ?? '');
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.select.deshabilitado.set(deshabilitado);
  }
}
