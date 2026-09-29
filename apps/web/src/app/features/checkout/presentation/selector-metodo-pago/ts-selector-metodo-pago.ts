import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MetodoPago } from '../../domain/pedido.model';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { iconoVisto } from '../../../../shared/ui/icono/iconos';

export interface OpcionMetodoPago {
  readonly valor: MetodoPago;
  readonly etiqueta: string;

  /**
   * Lo que el botón no puede decir con su nombre, **al frente del botón y fuera de él**. Opcional
   * porque no todos lo necesitan: "Sistecrédito" y "Pago contraentrega" se explican solos.
   *
   * <p>Nació con el agrupamiento de los medios de Wompi (28 de septiembre de 2026). Un botón que
   * dijera solo "Wompi" sería peor que los cuatro que reemplaza: nombra a la pasarela, que al
   * comprador no le dice nada, en vez del medio, que es lo que él reconoce. La descripción es la
   * que devuelve esa información — sin volver a prometer cuál se usará, que es lo que no se puede
   * prometer.
   *
   * <p>Vivió dentro del botón hasta ese mismo día; se pidió sacarla al lado. Lo que el cambio
   * cuesta —y por eso está escrito en la plantilla— es que el nombre accesible ya no la incluye:
   * ahora la enlaza un `aria-describedby`.
   */
  readonly detalle?: string;
}

/** Mismo patrón de "botones de alternancia" que `ts-selector-variante`: no es
 * un `ControlValueAccessor` porque no hay ningún `FormGroup` de la Fase 3 que
 * necesite bindearlo con `formControlName` — quien lo usa maneja el valor
 * elegido como una señal propia. */
@Component({
  selector: 'ts-selector-metodo-pago',
  imports: [TsBoton, TsIcono],
  templateUrl: './ts-selector-metodo-pago.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorMetodoPago {
  readonly opciones = input.required<readonly OpcionMetodoPago[]>();
  readonly seleccionado = input<MetodoPago | null>(null);
  readonly etiquetaGrupo = input.required<string>();

  readonly seleccionCambio = output<MetodoPago>();

  protected readonly iconoVisto = iconoVisto;

  protected estaSeleccionado(opcion: OpcionMetodoPago): boolean {
    return this.seleccionado() === opcion.valor;
  }

  /**
   * Lo que cambia entre una baldosa elegida y una en reposo: **el color del borde**, nunca su
   * ancho — la variante `baldosa` lo fija en 2 px justamente para que todas midan igual, elegidas
   * o no.
   *
   * <p>`border-current oscuro:border-current` con el `oscuro:` explícito y aparentemente redundante,
   * y no lo es: `cn` (tailwind-merge) agrupa por modificador, así que un `border-current` a secas
   * dejaría vivo el `oscuro:border-ts-acento` de la variante y en tema oscuro el contorno del
   * elegido volvería a ser ámbar sobre ámbar — o sea, invisible. Con el par, los dos se
   * reemplazan.
   *
   * <p>`justify-start` en las dos ramas porque el disco va a la izquierda: con el `justify-center`
   * de la base, las etiquetas quedarían centradas y cada una empezaría en un sitio distinto dentro
   * de una columna donde todos los botones miden lo mismo.
   */
  protected claseDeBoton(opcion: OpcionMetodoPago): string {
    return this.estaSeleccionado(opcion)
      ? 'w-full justify-start border-current oscuro:border-current'
      : 'w-full justify-start';
  }

  /** El `id` del párrafo que describe a esta opción, para el `aria-describedby` de su botón. */
  protected idDeDetalle(opcion: OpcionMetodoPago): string {
    return `metodo-pago-detalle-${opcion.valor.toLowerCase()}`;
  }

  protected elegir(opcion: OpcionMetodoPago): void {
    this.seleccionCambio.emit(opcion.valor);
  }
}
