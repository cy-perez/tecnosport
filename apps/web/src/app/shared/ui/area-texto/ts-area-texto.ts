import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { cn } from '../cn';
import { CLASES_AYUDA, CLASES_CONTROL, CLASES_ERROR, CLASES_ETIQUETA } from '../clases-control';

// eslint-disable-next-line @typescript-eslint/no-empty-function -- valor por defecto hasta que Forms registre el real
function sinOperacion(): void {}

/**
 * Un campo de texto largo.
 *
 * <p><b>Es otro componente y no un `tipo` de `ts-campo`</b>, por el mismo motivo por el que
 * `ts-icono-marca` no es un modo de `ts-icono`: `<textarea>` y `<input>` no comparten API. El
 * primero no tiene `type` ni `value` como atributo —su valor es su contenido—, sí tiene `rows`, y
 * se dimensiona de otra forma. Meterlo en `ts-campo` habría convertido un componente con una regla
 * en uno con una excepción, y la plantilla tendría dos ramas que alguien tendría que mantener
 * iguales.
 *
 * <p>Lo que sí comparte es todo lo que se ve y todo lo que se anuncia, y eso no está duplicado:
 * sale de `clases-control.ts`, igual que `ts-campo` y `ts-select`. Mismo borde, mismo relleno,
 * mismo anillo de foco, misma etiqueta, mismo mensaje de error, mismo `aria-required`.
 *
 * <h2>El contador</h2>
 *
 * <p>Cuando se le da un `maximo`, el control pinta cuántos caracteres quedan y además lo pone en el
 * `maxlength` del elemento. Las dos cosas hacen falta y no se sustituyen: el `maxlength` impide
 * pasarse, y el contador es lo que explica por qué el campo dejó de aceptar teclas — sin él, un
 * campo que se queda mudo parece roto.
 *
 * <p>El tope real lo sigue decidiendo el servidor (`Sugerencia.MAXIMO_CARACTERES_MENSAJE`): esto es
 * cortesía para quien escribe, no una validación. La ruta es pública y un `maxlength` del navegador
 * no protege nada (regla dura #7).
 */
@Component({
  selector: 'ts-area-texto',
  templateUrl: './ts-area-texto.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TsAreaTexto),
      multi: true,
    },
  ],
})
export class TsAreaTexto implements ControlValueAccessor {
  /** No se llama `id`, por lo mismo que en `ts-campo`: Angular no lo renombra en el DOM. */
  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly error = input<string | null>(null);
  readonly obligatorio = input(false);
  readonly placeholder = input<string | null>(null);
  readonly ayuda = input<string | null>(null);

  /** Cuántas líneas se ven sin desplazar. Cuatro es lo que cabe sin dominar una columna. */
  readonly filas = input(4);

  /** El tope de caracteres, si lo hay. Pinta el contador y pone el `maxlength`. */
  readonly maximo = input<number | null>(null);

  /**
   * El texto del contador, que lo arma quien usa el componente: `shared/ui/` es la capa tonta y no
   * traduce nada (`apps/web/CLAUDE.md`). Recibe los caracteres que quedan.
   */
  readonly textoContador = input<(restantes: number) => string>((restantes) => String(restantes));

  protected readonly clasesControl = cn(CLASES_CONTROL, 'resize-y');
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesError = CLASES_ERROR;
  protected readonly clasesAyuda = CLASES_AYUDA;

  protected readonly valorMostrado = signal('');
  protected readonly deshabilitado = signal(false);

  protected readonly contador = computed(() => {
    const tope = this.maximo();
    return tope === null ? '' : this.textoContador()(tope - this.valorMostrado().length);
  });

  /** La ayuda, el contador y el error a la vez cuando los hay: los tres describen el control. */
  protected readonly descripcion = computed(() => {
    const partes: string[] = [];
    if (this.ayuda()) {
      partes.push(this.idCampo() + '-ayuda');
    }
    if (this.maximo() !== null) {
      partes.push(this.idCampo() + '-contador');
    }
    if (this.error()) {
      partes.push(this.idCampo() + '-error');
    }
    return partes.length === 0 ? null : partes.join(' ');
  });

  private alCambiar: (valor: string) => void = sinOperacion;
  private alTocar: () => void = sinOperacion;

  writeValue(valor: string | null): void {
    this.valorMostrado.set(valor ?? '');
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

  protected manejarEntrada(valorCrudo: string): void {
    this.valorMostrado.set(valorCrudo);
    this.alCambiar(valorCrudo);
  }

  protected manejarSalida(): void {
    this.alTocar();
  }
}
