import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BrnPopoverImports } from '@spartan-ng/brain/popover';
import { formatearPrecio } from '../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { iconoCerrar, iconoEnvio } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { TsLogoPago } from '../../../../shared/ui/icono/ts-logo-pago';
import { OpcionEnvio } from '../../domain/envio.model';
import { logoDeTransportadora } from './logo-de-transportadora';

/**
 * Las transportadoras que cotizaron este envío, para que el comprador elija con cuál (ADR-0073). Se
 * abre al pulsar «Continuar» en el resumen, anclado a ese botón, y se cierra con Escape, al pulsar
 * fuera o con la equis.
 *
 * <h2>Por qué un popover y no un diálogo</h2>
 *
 * <p>Lo pidió el negocio, y además encaja: es una sola decisión, al pie de un formulario que la
 * persona acaba de llenar, y no hace falta tapar la página para tomarla. Lo pone el popover de
 * Spartan, el mismo proveedor del `ts-dialogo`, que trae lo que no se puede hacer a mano sin errores:
 * el foco entra al abrir y vuelve al botón al cerrar, y el panel se recoloca al desplazarse.
 *
 * <p>**El `role` lo declara este contenedor y no el panel de Spartan.** Spartan pone `dialog` en el
 * panel sin un `aria-labelledby`, y un diálogo sin nombre se anuncia como "diálogo" a secas. Aquí el
 * panel va sin rol y el `<div>` de dentro lleva los dos.
 *
 * <h2>Los botones miden todos lo mismo</h2>
 *
 * <p>Una rejilla de columnas iguales y cada botón a `w-full`: el ancho de columna lo pone el
 * contenido más largo —"Inter Rapidísimo" con su logo—, así que todos toman el del más grande sin
 * fijar ningún ancho a mano. Son la variante `baldosa`, la de los métodos de pago, con el logo a la
 * izquierda; debajo, el costo en negrita y del mismo tamaño de letra que el botón.
 *
 * <p>El costo va **fuera** del botón, como lo pidió el negocio, y por eso se enlaza con
 * `aria-describedby` (`ts-boton.descritoPor`): fuera de él, para un lector de pantalla sería un
 * párrafo suelto sin relación con la opción.
 */
@Component({
  selector: 'ts-popover-transportadora',
  imports: [BrnPopoverImports, TranslocoPipe, TsBoton, TsIcono, TsLogoPago],
  templateUrl: './ts-popover-transportadora.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsPopoverTransportadora {
  private readonly transloco = inject(TranslocoService);

  readonly abierto = input.required<boolean>();
  /** El botón al que se ancla: el «Continuar» del resumen. */
  readonly origen = input.required<ElementRef<HTMLElement> | HTMLElement | null>();
  readonly opciones = input.required<readonly OpcionEnvio[]>();

  readonly elegir = output<string>();
  readonly cerrar = output<void>();

  protected readonly iconoCerrar = iconoCerrar;
  protected readonly iconoEnvio = iconoEnvio;
  protected readonly logoDe = logoDeTransportadora;

  protected precio(opcion: OpcionEnvio): string {
    return formatearPrecio(opcion.costoEnvio, opcion.moneda, this.transloco.getActiveLang());
  }

  protected idDePrecio(indice: number): string {
    return `transportadora-precio-${indice}`;
  }

  /** Spartan avisa de cada cambio de estado; solo el cierre le interesa a quien lo abrió. */
  protected alCambiarEstado(estado: 'open' | 'closed'): void {
    if (estado === 'closed' && this.abierto()) {
      this.cerrar.emit();
    }
  }
}
