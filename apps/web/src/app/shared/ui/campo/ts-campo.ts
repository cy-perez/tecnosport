import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import type { IconNode } from 'lucide';
import { cn } from '../cn';
import { CLASES_AYUDA, CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';
import { TsIcono } from '../icono/ts-icono';

/**
 * `datetime-local` entró con la bandeja de atención: quien radica una PQR escribe la fecha en que
 * llegó, que es de la que cuelga el plazo legal, y no siempre es "ahora". El control nativo del
 * navegador es lo que hay; un selector propio sería otro componente y otra auditoría de foco.
 */
export type TipoCampo =
  'text' | 'number' | 'search' | 'password' | 'email' | 'tel' | 'datetime-local';

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
  imports: [TsIcono],
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
   * Un icono dentro del campo, a la izquierda, como ancla de qué se escribe ahí.
   *
   * Cuando lo hay, el relleno del control se corre a `ps-48` para que el texto no lo pise. Nunca
   * es el nombre accesible —`ts-icono` pinta `aria-hidden`—: eso sigue siendo la etiqueta.
   */
  readonly icono = input<IconNode | null>(null);

  /**
   * El texto de sugerencia del control.
   *
   * Existe desde que las pantallas de cuenta esconden la etiqueta (`etiquetaOculta`): ahí el
   * placeholder es lo único que queda en pantalla nombrando el campo, así que es obligatorio
   * ponerlo. Donde la etiqueta se ve, es opcional y suele sobrar.
   */
  readonly placeholder = input<string | null>(null);

  /**
   * Esconde la etiqueta a la vista, dejándola para la tecnología de apoyo.
   *
   * **No es gratis y conviene saber qué se paga**: el placeholder desaparece al primer carácter,
   * así que quien vuelve al formulario a medio llenar ya no tiene en pantalla el nombre del campo
   * —solo el icono—. Se usa donde el formulario es corto y los campos son obvios (entrar, crear
   * cuenta); en el checkout, en el panel y en cualquier formulario largo, la etiqueta se ve.
   */
  readonly etiquetaOculta = input(false);

  /**
   * Texto de apoyo debajo de la etiqueta, atado al control con `aria-describedby`.
   *
   * Entró porque escribirlo como un `<p>` suelto antes del componente lo deja visualmente pegado al
   * campo <b>anterior</b> —se vio en la bandeja de PQR, donde la ayuda de "fecha en que llegó"
   * parecía ser del asunto— y además un lector de pantalla nunca lo relacionaba con nada.
   */
  readonly ayuda = input<string | null>(null);

  /** `p-12` de la base y `ps-48` encima cuando hay ancla: `cn` resuelve el conflicto de relleno
   * inicial a favor de lo último, que es justo lo que hace falta. */
  protected readonly clasesControl = computed(() =>
    cn(CLASES_CONTROL, this.icono() ? 'ps-48' : ''),
  );
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesError = CLASES_ERROR;
  protected readonly clasesAyuda = CLASES_AYUDA;

  /**
   * Los dos textos de apoyo a la vez cuando los hay: sin esto, mostrar un error dejaba la ayuda
   * fuera del nombre accesible del control justo cuando más falta hace.
   */
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
    this.alCambiar(
      this.tipo() === 'number' ? (valorCrudo === '' ? null : Number(valorCrudo)) : valorCrudo,
    );
  }

  protected manejarSalida(): void {
    this.alTocar();
  }
}
