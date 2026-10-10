import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { usarEliminarPedido } from '../../application/eliminar-pedido.mutacion';

/**
 * Eliminar un pedido del todo, al final de la fila expandida, como los paneles de retracto y
 * garantía. Quien lo muestra decide si el estado lo admite (pago fallido o cancelado); lo que
 * cuelga del pedido lo decide el servidor, y su 409 se dice aquí.
 *
 * Al eliminar, la fila desaparece con este componente dentro: el aviso no puede vivir aquí, así que
 * se emite `eliminado` con el número y la lista lo dice en su propia región.
 */
@Component({
  selector: 'app-eliminar-pedido',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './eliminar-pedido.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EliminarPedido {
  readonly pedidoId = input.required<string>();
  readonly numeroPedido = input.required<string>();
  readonly eliminado = output<string>();

  private readonly transloco = inject(TranslocoService);
  private readonly mutacion = usarEliminarPedido();
  private enVuelo = false;
  protected readonly eliminando = computed(() => this.mutacion.isPending());
  protected readonly confirmando = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly boton = viewChild('boton', { read: ElementRef });

  protected preguntar(): void {
    this.error.set(null);
    this.confirmando.set(true);
    this.enfocarDespuesDePintar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    this.confirmando.set(false);
    this.error.set(null);
    this.enfocarDespuesDePintar(() => this.boton()?.nativeElement.querySelector('button'));
  }

  /** Guarda de reentrada: el botón usa `[ocupado]`, no `[cargando]`, y sigue siendo pulsable. */
  protected eliminar(): void {
    if (this.enVuelo) {
      return;
    }
    this.enVuelo = true;
    this.error.set(null);
    const numero = this.numeroPedido();
    this.mutacion.mutate(this.pedidoId(), {
      onSettled: () => (this.enVuelo = false),
      onSuccess: () => this.eliminado.emit(numero),
      onError: (error: unknown) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.pedidos.eliminar.error')),
    });
  }
}
