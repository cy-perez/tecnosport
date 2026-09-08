import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';

export interface OpcionSelect {
  readonly valor: string;
  readonly etiqueta: string;
}

/**
 * Sin `ControlValueAccessor` a propósito: el puente con los formularios de Angular vive en
 * `TsSelectControl`, una directiva aparte. Cuando lo implementaba este componente, importarlo
 * arrastraba `@angular/forms` entero (38,6 kB) al paquete inicial, y quien lo importa siempre es
 * el encabezado —los selectores de idioma y de tema— o sea todas las pantallas, incluidas la
 * portada, la rejilla y la ficha, que no tienen un solo formulario. Quien necesita el puente lo
 * importa; quien solo necesita un `<select>` con etiqueta, anillo de foco y objetivo táctil, no.
 */
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
})
export class TsSelect {
  protected readonly clasesControl = CLASES_CONTROL;
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesError = CLASES_ERROR;

  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly opciones = input.required<readonly OpcionSelect[]>();
  /** Texto de la opción vacía, para un select que representa "sin filtro". */
  readonly placeholder = input<string | null>(null);
  readonly error = input<string | null>(null);

  /** Escribible desde fuera: así lo mueve `TsSelectControl` sin que este componente sepa que
   * existen los formularios de Angular. */
  readonly valor = model('');
  readonly deshabilitado = model(false);
  /** Solo la elección del usuario. `valor` también cambia cuando lo escribe un formulario, y un
   * `writeValue` no es una interacción: emitir ahí marcaría el control como sucio solo. */
  readonly cambio = output<string>();

  protected manejarCambio(valor: string): void {
    this.valor.set(valor);
    this.cambio.emit(valor);
  }
}
