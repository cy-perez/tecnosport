import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';

/**
 * `datetime-local` entró con la bandeja de atención: quien radica una PQR escribe la fecha en que
 * llegó, que es de la que cuelga el plazo legal, y no siempre es "ahora". El control nativo del
 * navegador es lo que hay; un selector propio sería otro componente y otra auditoría de foco.
 */
export type TipoCampo =
  | 'text'
  | 'number'
  | 'search'
  | 'password'
  | 'email'
  | 'datetime-local';

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

/**
 * `min-w-0` en el host, y no es un detalle. Un ítem de grid o de flex arranca
 * con `min-width: auto`, así que no puede encogerse por debajo del ancho
 * mínimo de su contenido; un `<select>` mide por su opción más larga, y un
 * texto largo ensanchaba su columna comprimiendo las demás — por eso los
 * filtros del catálogo se veían de tamaños distintos. Viene del SCSS que este
 * componente reemplaza y se conserva a propósito.
 *
 * El aspecto del control sale de `clases-control.ts`, compartido con
 * `ts-select`: son dos controles nativos distintos que tienen que verse
 * idénticos, y tenerlo duplicado es la forma de que un día dejen de coincidir.
 */
@Component({
  selector: 'ts-campo',
  templateUrl: './ts-campo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsCampo),
      multi: true,
    },
  ],
})
export class TsCampo implements ControlValueAccessor {
  /**
   * No se llama `id`: Angular no renombra un `input()` en el DOM, así que el
   * host terminaría con el mismo `id` que el control interno y cualquier
   * `<label for>` —o `getByLabelText`— apuntaría al host y no al campo real.
   */
  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly tipo = input<TipoCampo>('text');
  readonly error = input<string | null>(null);

  protected readonly clasesControl = CLASES_CONTROL;
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesError = CLASES_ERROR;

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
