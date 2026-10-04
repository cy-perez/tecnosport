import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { usarFoco } from '../../foco/foco';
import { CLASES_AYUDA, CLASES_CONTROL, CLASES_ETIQUETA } from '../clases-control';
import { cn } from '../cn';
import { TsCheckbox } from '../checkbox/ts-checkbox';
import { TsIcono } from '../icono/ts-icono';
import { iconoChevron } from '../icono/iconos';
import { ParteDeMuestra } from '../muestra-color/muestra-color.model';
import { TsMuestraColor } from '../muestra-color/ts-muestra-color';

/** Un color que se puede marcar: su valor, lo que se lee y cómo se pinta. */
export interface OpcionColor {
  readonly valor: string;
  readonly etiqueta: string;
  readonly muestra: ParteDeMuestra;
}

/** Los textos del control, **ya traducidos** por quien lo usa: `shared/ui` no conoce Transloco. */
export interface TextosSelectorColores {
  /** Lo que dice el botón cuando no hay ninguno marcado. */
  readonly ninguno: string;
  /** La etiqueta del buscador dentro de la lista. */
  readonly buscar: string;
  /** El aviso cuando ya se marcaron todos los que caben. */
  readonly maximo: string;
  /** Cuando el buscador no encuentra nada. */
  readonly sinResultados: string;
}

/**
 * Elegir de uno a tres colores de la paleta, en orden, con casillas en una lista desplegable
 * (4 de octubre de 2026). El orden es el de marcado: el primero que se marca es la primera porción
 * del círculo, y la lista lo dice con su número al lado. Desmarcar uno corre a los siguientes.
 *
 * Es un botón que abre una región, no un `<select multiple>`: el nativo no conserva el orden en
 * que se eligió, y aquí el orden es el dato. El botón declara `aria-expanded` y `aria-controls`;
 * Escape cierra la lista y devuelve el foco al botón. Al llegar al máximo, las demás casillas se
 * deshabilitan y un aviso lo dice: desmarcar una vuelve a abrirlas.
 */
@Component({
  selector: 'ts-selector-colores',
  imports: [TsCheckbox, TsIcono, TsMuestraColor],
  templateUrl: './ts-selector-colores.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorColores {
  /** No se llama `id`: Angular no lo renombra en el DOM (ver `ts-campo`). */
  readonly idCampo = input.required<string>();
  readonly label = input.required<string>();
  readonly opciones = input.required<readonly OpcionColor[]>();
  /** Los valores marcados, en orden. */
  readonly seleccion = input.required<readonly string[]>();
  readonly maximo = input(3);
  readonly textos = input.required<TextosSelectorColores>();
  readonly ayuda = input<string | null>(null);
  readonly obligatorio = input(false);

  readonly seleccionCambio = output<string[]>();

  protected readonly abierto = signal(false);
  protected readonly filtro = signal('');

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly boton = viewChild<ElementRef<HTMLButtonElement>>('boton');
  private readonly buscador = viewChild<ElementRef<HTMLInputElement>>('buscador');

  protected readonly iconoChevron = iconoChevron;
  protected readonly clasesEtiqueta = CLASES_ETIQUETA;
  protected readonly clasesAyuda = CLASES_AYUDA;
  protected readonly clasesBuscador = CLASES_CONTROL;
  protected readonly clasesBoton = cn(
    CLASES_CONTROL,
    'flex cursor-pointer items-center gap-12 text-start',
  );

  /** Las opciones marcadas, en el orden de marcado; las que ya no existan se ignoran. */
  protected readonly marcadas = computed<OpcionColor[]>(() => {
    const porValor = new Map(this.opciones().map((opcion) => [opcion.valor, opcion]));
    return this.seleccion()
      .map((valor) => porValor.get(valor))
      .filter((opcion): opcion is OpcionColor => opcion !== undefined);
  });

  protected readonly muestra = computed<ParteDeMuestra[]>(() =>
    this.marcadas().map((opcion) => opcion.muestra),
  );

  protected readonly resumen = computed(() => {
    const marcadas = this.marcadas();
    return marcadas.length === 0
      ? this.textos().ninguno
      : marcadas.map((opcion) => opcion.etiqueta).join(' / ');
  });

  protected readonly lleno = computed(() => this.marcadas().length >= this.maximo());

  protected readonly visibles = computed<OpcionColor[]>(() => {
    const buscado = plano(this.filtro());
    return this.opciones().filter(
      (opcion) => buscado === '' || plano(opcion.etiqueta).includes(buscado),
    );
  });

  protected readonly idLista = computed(() => this.idCampo() + '-lista');
  protected readonly idAyuda = computed(() => this.idCampo() + '-ayuda');

  /** El número de orden de una opción marcada, o `null`. */
  protected posicion(valor: string): number | null {
    const indice = this.seleccion().indexOf(valor);
    return indice < 0 ? null : indice + 1;
  }

  /** Una marcada dice su lugar en la combinación: «Rojo (2)». */
  protected etiquetaDe(opcion: OpcionColor): string {
    const posicion = this.posicion(opcion.valor);
    return posicion === null ? opcion.etiqueta : `${opcion.etiqueta} (${posicion})`;
  }

  protected alternar(): void {
    if (this.abierto()) {
      this.cerrar();
      return;
    }
    this.filtro.set('');
    this.abierto.set(true);
    this.enfocarDespuesDePintar(() => this.buscador()?.nativeElement);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.enfocarDespuesDePintar(() => this.boton()?.nativeElement);
  }

  protected marcar(valor: string, marcado: boolean): void {
    const actual = this.seleccion().filter((v) => v !== valor);
    if (marcado) {
      if (actual.length >= this.maximo()) {
        return;
      }
      this.seleccionCambio.emit([...actual, valor]);
      return;
    }
    this.seleccionCambio.emit(actual);
  }

  protected deshabilitada(valor: string): boolean {
    return this.lleno() && this.posicion(valor) === null;
  }
}

function plano(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
