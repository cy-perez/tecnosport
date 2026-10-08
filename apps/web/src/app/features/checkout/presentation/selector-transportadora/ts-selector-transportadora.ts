import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { formatearPrecio } from '../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { iconoEnvio, iconoVisto } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { TsLogoPago } from '../../../../shared/ui/icono/ts-logo-pago';
import { OpcionEnvio } from '../../domain/envio.model';
import { logoDeTransportadora } from './logo-de-transportadora';

/**
 * Las transportadoras que cotizaron este envío, para que el comprador elija con cuál (ADR-0073).
 *
 * <p>Vivió en un popover anclado al «Continuar» del resumen hasta el 8 de octubre de 2026; se pidió
 * llevarlo a una página propia, como los métodos de pago, y este selector es su mitad visual. Copia
 * la forma de `ts-selector-metodo-pago` —baldosas de alternancia con `aria-pressed` y el disco de un
 * radio— para que los dos pasos seguidos se lean igual.
 *
 * <h2>Los botones miden todos lo mismo</h2>
 *
 * <p>La rejilla es `w-fit` y sus columnas son fraccionarias: en un contenedor que toma el ancho de su
 * contenido, las columnas `fr` se igualan a la más ancha, así que todas toman el ancho del botón más
 * largo —"Inter Rapidísimo" con su logo— sin fijar ningún ancho a mano. Cada botón llena la suya con
 * `w-full`. Debajo, el costo en negrita y del mismo tamaño de letra que el botón.
 *
 * <p>El costo va **fuera** del botón, como lo pidió el negocio, y por eso se enlaza con
 * `aria-describedby` (`ts-boton.descritoPor`): fuera de él, para un lector de pantalla sería un
 * párrafo suelto sin relación con la opción.
 */
@Component({
  selector: 'ts-selector-transportadora',
  imports: [TsBoton, TsIcono, TsLogoPago],
  templateUrl: './ts-selector-transportadora.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSelectorTransportadora {
  private readonly transloco = inject(TranslocoService);

  readonly opciones = input.required<readonly OpcionEnvio[]>();
  /** Por nombre, como la guarda `CheckoutStore`. */
  readonly seleccionada = input<string | null>(null);
  readonly etiquetaGrupo = input.required<string>();

  readonly seleccionCambio = output<string>();

  protected readonly iconoEnvio = iconoEnvio;
  protected readonly iconoVisto = iconoVisto;
  protected readonly logoDe = logoDeTransportadora;

  /** Sin mayúsculas ni espacios de sobra, como compara el servidor (`opcionDeTransportadora`). */
  protected estaSeleccionada(opcion: OpcionEnvio): boolean {
    const elegida = this.seleccionada();
    return (
      elegida !== null &&
      elegida.trim().toLowerCase() === opcion.transportadora.trim().toLowerCase()
    );
  }

  /** Lo mismo que `ts-selector-metodo-pago.claseDeBoton`, y por las mismas razones. */
  protected claseDeBoton(opcion: OpcionEnvio): string {
    return this.estaSeleccionada(opcion)
      ? 'w-full justify-start border-current oscuro:border-current'
      : 'w-full justify-start';
  }

  protected precio(opcion: OpcionEnvio): string {
    return formatearPrecio(opcion.costoEnvio, opcion.moneda, this.transloco.getActiveLang());
  }

  protected idDePrecio(indice: number): string {
    return `transportadora-precio-${indice}`;
  }
}
