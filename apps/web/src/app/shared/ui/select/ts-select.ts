import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { CLASES_AYUDA, CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';

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
  protected readonly clasesAyuda = CLASES_AYUDA;

  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly opciones = input.required<readonly OpcionSelect[]>();
  /** Texto de la opción vacía, para un select que representa "sin filtro". */
  readonly placeholder = input<string | null>(null);
  readonly error = input<string | null>(null);

  /**
   * Si el campo es obligatorio. Pinta un asterisco junto a la etiqueta y, lo que de verdad importa,
   * declara `aria-required="true"`.
   *
   * **Faltaba en todo el sitio**: hay 65 `Validators.required` en el frontend y no habia un solo
   * `required` ni `aria-required` en ninguna plantilla. Con lector de pantalla, quien entraba a
   * "Iniciar sesion" oia "Correo, editar" y "Clave, editar" —sin "obligatorio"—, llegaba a un boton
   * anunciado como no disponible y no habia nada en la pagina que explicara que faltaba. WCAG 3.3.2.
   *
   * Es `aria-required` y no el `required` nativo a proposito: el `required` del navegador dispara
   * su propia burbuja de validacion, sin traducir y fuera del sistema visual, y este proyecto ya
   * valida al enviar con sus mensajes de Transloco. Lo que hace falta es que la tecnologia de apoyo
   * lo sepa, no que el navegador se meta.
   *
   * El asterisco va `aria-hidden`: el nombre accesible del campo ya lo lleva `aria-required`, y
   * leerlo ademas como "asterisco" seria ruido.
   */
  readonly obligatorio = input(false);

  /**
   * Texto de apoyo debajo de la etiqueta, atado al control con `aria-describedby`.
   *
   * Lo mismo que ya tenía `ts-campo`, y por el mismo motivo: escribirlo como un `<p>` suelto antes
   * del componente lo deja visualmente pegado al campo **anterior** y ningún lector de pantalla lo
   * relaciona con nada. Faltaba aquí, y se notó al explicar por qué el medio de reintegro que pide
   * el comprador no se puede corregir después — una advertencia legal que el lector de pantalla no
   * asocia al control es una advertencia que no está.
   */
  readonly ayuda = input<string | null>(null);

  /** Los dos textos de apoyo a la vez cuando los hay, igual que en `ts-campo`. */
  protected readonly descripcion = computed(() => {
    const partes: string[] = [];
    if (this.ayuda()) {
      partes.push(this.idCampo() + '-ayuda');
    }
    if (this.error()) {
      partes.push(this.idCampo() + '-error');
    }
    return partes.length === 0 ? null : partes.join(' ');
  });

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
