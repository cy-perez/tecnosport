import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import {
  ResultadoDeLimpieza,
  usarBorrarProductosNoPublicados,
  usarContarProductosNoPublicados,
} from '../../application/borrar-no-publicados.mutacion';

/**
 * El borrado en bloque de lo no publicado, encima de la lista de productos. Un componente aparte
 * porque tiene su propio ritmo —contar, preguntar, pedir tandas— y la lista ya maneja la
 * confirmación por fila de publicar, retirar y borrar uno.
 *
 * Sin productos en borrador no ofrece nada; el aviso de lo hecho vive siempre en el DOM, porque
 * una región cortés que nace llena no se anuncia.
 */
@Component({
  selector: 'app-limpiar-no-publicados',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './limpiar-no-publicados.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LimpiarNoPublicados {
  private readonly transloco = inject(TranslocoService);

  private readonly consulta = usarContarProductosNoPublicados();
  protected readonly cantidad = computed(() => this.consulta.data() ?? 0);
  private readonly borrar = usarBorrarProductosNoPublicados();
  private enVuelo = false;
  protected readonly borrando = computed(() => this.borrar.isPending());
  protected readonly confirmando = signal(false);
  protected readonly eliminadosHastaAhora = signal(0);
  protected readonly aviso = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private readonly boton = viewChild('boton', { read: ElementRef });
  private readonly avisoRef = viewChild<ElementRef<HTMLElement>>('avisoRef');

  protected preguntar(): void {
    this.error.set(null);
    this.aviso.set(null);
    this.confirmando.set(true);
    // Sin esto, tabular desde el botón salta directo a "Sí, borrar todos" sin pasar por la
    // advertencia de lo que se lleva por delante.
    this.enfocarDespuesDePintar(() => this.caja()?.nativeElement);
  }

  protected cancelar(): void {
    this.confirmando.set(false);
    this.error.set(null);
    this.enfocarDespuesDePintar(() => this.boton()?.nativeElement.querySelector('button'));
  }

  /** La caja desaparece con el botón dentro: el foco va al aviso de lo que se borró. */
  protected confirmar(): void {
    // Una marca propia y no `borrando()`: `isPending` no cambia en el mismo tic del `mutate`.
    if (this.enVuelo) {
      return;
    }
    this.enVuelo = true;
    this.error.set(null);
    this.eliminadosHastaAhora.set(0);
    this.borrar.mutate(
      { alAvanzar: (eliminados) => this.eliminadosHastaAhora.set(eliminados) },
      {
        onSettled: () => (this.enVuelo = false),
        onSuccess: (resultado) => {
          this.confirmando.set(false);
          this.aviso.set(this.textoDeHecho(resultado));
          this.enfocarDespuesDePintar(() => this.avisoRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.error.set(mensajeDeError(error, this.transloco, 'admin.productos.limpiar.error')),
      },
    );
  }

  private textoDeHecho({ eliminados, conservados }: ResultadoDeLimpieza): string {
    return conservados > 0
      ? this.transloco.translate('admin.productos.limpiar.hechoConConservados', {
          eliminados,
          conservados,
        })
      : this.transloco.translate('admin.productos.limpiar.hecho', { eliminados });
  }
}
